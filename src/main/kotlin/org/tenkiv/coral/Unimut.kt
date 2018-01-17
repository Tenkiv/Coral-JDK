package org.tenkiv.coral

import java.util.concurrent.locks.*
import kotlin.concurrent.withLock
import kotlin.reflect.KProperty


private const val UNITIALIZED_MSG = "Attempted to access unimut property before it was set"

/**
 * Creates a delegate for properties that you want to mutate / set exactly one time but may not have the necessary
 * information to do so at compile time.
 *
 * @param onSet is called after setting the backing field to the new value.
 * @param onGet is called before returning the value of the property.
 */
fun <T : Any> unimut(
    concurrencyMode: UniMutConcurrencyMode = UniMutConcurrencyMode.NONE,
    onGet: ((T) -> Unit)? = null,
    onSet: ((T) -> Unit)? = null
): UniMutDelegate<T> =
    when (concurrencyMode) {
        UniMutConcurrencyMode.BLOCKING -> BlockingUniMutDelegate(onSet, onGet)
        UniMutConcurrencyMode.SYNCHRONIZED -> SynchronisedUniMutDelegate(onSet, onGet)
        UniMutConcurrencyMode.PUBLICATION -> PublicationSafeUnitMutDelegate(onSet, onGet)
        UniMutConcurrencyMode.NONE -> UniMutDelegate(onSet, onGet)
    }

/**
 * Creates a Synchronised unimut and allows use of a custom ReadWriteLock. This is only recommended for advanced users
 * who have a specific reason for wanting to do this.
 */
fun <T : Any> unimut(
    lock: ReadWriteLock,
    onGet: ((T?) -> Unit)? = null,
    onSet: ((T) -> Unit)? = null
): UniMutDelegate<T> = SynchronisedUniMutDelegate(onSet, onGet, lock)

/**
 * Creates a Blocking unimut and allows use of a custom Lock. This is only recommended for advanced users
 * who have a specific reason for wanting to do this.
 */
fun <T : Any> unimut(
    lock: Lock,
    onGet: ((T?) -> Unit)? = null,
    onSet: ((T) -> Unit)? = null
): UniMutDelegate<T> = BlockingUniMutDelegate(onSet, onGet, lock)

enum class UniMutConcurrencyMode {
    /**
     * Blocks the thread attempting to get the property until the property is set.
     */
    BLOCKING,
    SYNCHRONIZED,
    PUBLICATION,
    NONE
}

open class UniMutDelegate<T : Any> internal constructor(
    protected open var onSet: ((T) -> Unit)?,
    protected val onGet: ((T) -> Unit)?
) {
    open var value: T? = null

    /**
     * @throws UninitializedPropertyAccessException
     */
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        onGet?.invoke(
            value ?: throw UninitializedPropertyAccessException(UNITIALIZED_MSG)
        )
        return value ?: throw UninitializedPropertyAccessException(UNITIALIZED_MSG)
    }

    /**
     * @throws AlreadySetException
     */
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
        if (this.value == null) {
            this.value = value
            onSet?.invoke(value)
            onSet = null
        } else
            throw AlreadySetException("Attempted to set a unimut property that was already set.")

}

private class PublicationSafeUnitMutDelegate<T : Any>(
    @Volatile override var onSet: ((T) -> Unit)?,
    onGet: ((T) -> Unit)?
) : UniMutDelegate<T>(onSet, onGet) {
    @Volatile
    override var value: T? = null
}

private class SynchronisedUniMutDelegate<T : Any>(
    onSet: ((T) -> Unit)?,
    onGet: ((T) -> Unit)?,
    private val lock: ReadWriteLock = ReentrantReadWriteLock()
) : UniMutDelegate<T>(onSet, onGet) {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        value ?: lock.readLock().withLock { super.getValue(thisRef, property) }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
        lock.writeLock().withLock { super.setValue(thisRef, property, value) }

}

private class BlockingUniMutDelegate<T : Any>(
    onSet: ((T) -> Unit)?,
    onGet: ((T) -> Unit)?,
    private val lock: Lock = ReentrantLock()
) :
    UniMutDelegate<T>(onSet, onGet) {
    var setCondition: Condition? = lock.newCondition()

    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        value ?: lock.withLock {
            if (value != null) {
                onGet?.invoke(value!!)
                value!!
            } else {
                setCondition?.await()
                onGet?.invoke(value!!)
                value!!
            }
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
        lock.withLock {
            super.setValue(thisRef, property, value)
            setCondition?.signalAll()
            setCondition = null
        }

}

class AlreadySetException : UnsupportedOperationException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable) : super(message, cause)
}