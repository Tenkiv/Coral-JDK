/*
 * Copyright 2019 Tenkiv, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import org.jetbrains.kotlin.gradle.tasks.*
import java.io.FileInputStream
import java.util.Properties

plugins {
    kotlin("multiplatform") version Vof.kotlinVersion
    jacoco
    java
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version Vof.dokka
    `java-library`
}

buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }
}

kotlin {
    jvm {
        val main by compilations.getting {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            repositories {
                mavenCentral()
                jcenter()
            }

            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.spekframework.spek2:spek-dsl-metadata:${Vof.spek}")
            }
        }

        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                dependsOn(commonMain)
                implementation(kotlin("stdlib-jdk8"))
            }
        }

        jvm().compilations["test"].defaultSourceSet {
            repositories {
                mavenCentral()
                jcenter()
            }

            dependencies {
                implementation(kotlin("reflect", Vof.kotlinVersion))
                implementation(kotlin("test", Vof.kotlinVersion))


                implementation("org.spekframework.spek2:spek-dsl-jvm:${Vof.spek}") {
                    exclude("org.jetbrains.kotlin")
                }

                implementation("org.spekframework.spek2:spek-runner-junit5:${Vof.spek}") {
                    exclude("org.jetbrains.kotlin")
                    exclude("org.junit.platform")
                }

                implementation("org.junit.platform:junit-platform-launcher:${Vof.junitPlatform}")
            }

            jacoco {
                toolVersion = Vof.jacocoTool
            }
        }

        all {
            repositories {
                jcenter()
                mavenCentral()
            }
        }
    }
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.suppressWarnings = true
        kotlinOptions.jvmTarget = "1.8"
    }

    val jacocoReport = withType<JacocoReport> {
        reports {
            html.isEnabled = true
            xml.isEnabled = true
            csv.isEnabled = false
        }
    }

    named<Test>("jvmTest") {
        outputs.upToDateWhen { false }
        useJUnitPlatform {
            includeEngines("spek2")
        }

        maxHeapSize = "1g"
        finalizedBy(jacocoReport)
    }

    dokka {
        outputDirectory = "$buildDir/docs"
        impliedPlatforms = mutableListOf("Common")

        kotlinTasks { emptyList() }

        sourceRoot {
            path = kotlin.sourceSets.commonMain.get().kotlin.srcDirs.first().absolutePath
            platforms = listOf("Common")
        }

        sourceRoot {
            path = kotlin.jvm().compilations["main"].defaultSourceSet.kotlin.srcDirs.first().absolutePath
            platforms = listOf("JVM")
        }
    }

    register<Jar>("sourcesJar") {
        from(project.sourceSets.main.get().allJava)
        archiveClassifier.set("sources")
    }

    register<Jar>("javadocJar") {
        from(getByName("dokka"))
        archiveClassifier.set("javadoc")
    }
}

val isRelease = !version.toString().endsWith("SNAPSHOT")
val properties = Properties()
val propertiesFile = File(rootDir, "local.properties")
if (propertiesFile.canRead()) {
    properties.load(FileInputStream(propertiesFile))
}

extra["signing.keyId"] = properties.getProperty("SIGNING_KEYID")
extra["signing.secretKeyRingFile"] = properties.getProperty("SIGNING_SECRETKEYRINGFILE")
extra["signing.password"] = properties.getProperty("SIGNING_KEYPASSWORD")

publishing {
    publications {
        if (isRelease) {
            create<MavenPublication>("maven-coral-jvm") {
                val jvm by getting {
                    groupId = "org.tenkiv.coral"
                    artifactId = "coral-jvm"
                    version = project.version.toString()

                    from(components["java"])
                    artifact(tasks["sourcesJar"])
                    artifact(tasks["javadocJar"])

                    pom {
                        name.set(project.name)
                        description.set(Info.pomDescription)
                        url.set(Info.projectUrl)
                        licenses {
                            license {
                                name.set(Info.pomLicense)
                                url.set(Info.pomLicenseUrl)
                            }
                        }
                        organization {
                            name.set(Info.pomOrg)
                        }
                        scm {
                            connection.set(Info.projectCloneUrl)
                            url.set(Info.projectUrl)
                        }
                    }
                }
            }

            create<MavenPublication>("maven-coral-metadata") {
                val metadata by getting {
                    groupId = "org.tenkiv.coral"
                    artifactId = "coral-metadata"
                    version = project.version.toString()

                    from(components["java"])
                    artifact(tasks["sourcesJar"])
                    artifact(tasks["javadocJar"])

                    pom {
                        name.set(project.name)
                        description.set(Info.pomDescription)
                        url.set(Info.projectUrl)
                        licenses {
                            license {
                                name.set(Info.pomLicense)
                                url.set(Info.pomLicenseUrl)
                            }
                        }
                        organization {
                            name.set(Info.pomOrg)
                        }
                        scm {
                            connection.set(Info.projectCloneUrl)
                            url.set(Info.projectUrl)
                        }
                    }
                }
            }
        } else {
            create<MavenPublication>("maven-coral-jvm-snapshot") {
                val jvm by getting {
                    groupId = "org.tenkiv.coral"
                    artifactId = "coral-jvm"
                    version = project.version.toString()

                    from(components["java"])
                    artifact(tasks["sourcesJar"])
                    artifact(tasks["javadocJar"])

                    pom {
                        name.set(project.name)
                        description.set(Info.pomDescription)
                        url.set(System.getenv("CI_PROJECT_URL"))
                        licenses {
                            license {
                                name.set(Info.pomLicense)
                                url.set(Info.pomLicenseUrl)
                            }
                        }
                        organization {
                            name.set(Info.pomOrg)
                        }
                        scm {
                            connection.set(System.getenv("CI_REPOSITORY_URL"))
                            url.set(System.getenv("CI_PROJECT_URL"))
                        }
                    }
                }
            }

            create<MavenPublication>("maven-coral-metadata-snapshot") {
                val metadata by getting {
                    groupId = "org.tenkiv.coral"
                    artifactId = "coral-metadata"
                    version = project.version.toString()

                    from(components["java"])
                    artifact(tasks["sourcesJar"])
                    artifact(tasks["javadocJar"])

                    pom {
                        name.set(project.name)
                        description.set(Info.pomDescription)
                        url.set(System.getenv("CI_PROJECT_URL"))
                        licenses {
                            license {
                                name.set(Info.pomLicense)
                                url.set(Info.pomLicenseUrl)
                            }
                        }
                        organization {
                            name.set(Info.pomOrg)
                        }
                        scm {
                            connection.set(System.getenv("CI_REPOSITORY_URL"))
                            url.set(System.getenv("CI_PROJECT_URL"))
                        }
                    }
                }
            }
        }

        repositories {
            maven {
                val releasesRepoUrl = uri(Info.sonatypeReleaseRepoUrl)
                val snapshotsRepoUrl = uri(Info.sonatypeSnapshotRepoUrl)
                url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                credentials {
                    if (isRelease) {
                        username = properties.getProperty("MAVEN_USER")
                        password = properties.getProperty("MAVEN_PASSWORD")
                    } else {
                        username = System.getenv("MAVEN_REPO_USER")
                        password = System.getenv("MAVEN_REPO_PASSWORD")
                    }
                }
            }
        }
    }
}

signing {
    if (isRelease) {
        sign(publishing.publications["maven-coral-jvm"])
        sign(publishing.publications["maven-coral-metadata"])
    }
}