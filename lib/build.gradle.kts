import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spotless)
    alias(libs.plugins.dokka)

    `java-library`
    `maven-publish`
    signing
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // This dependency is exported to consumers, that is to say found on their compile classpath.
    api(libs.commons.math3)

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    implementation(libs.guava)
    testImplementation(kotlin("test"))
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use Kotlin Test test framework
            useJUnitJupiter()
        }
    }
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    withSourcesJar()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        ktlint(ktlintVersion)
    }
}

//
// Redefine javadocJar in terms of Dokka
//
val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

//
// Configuration of Dokka engine
//
tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("main") {
            documentedVisibilities.set(setOf(Visibility.PUBLIC, Visibility.PROTECTED))

            val remoteSourceUrl = System.getenv()["GIT_REF_NAME"]?.let { URL("https://github.com/routis/test-pub/tree/$it/lib/src") }
            remoteSourceUrl
                ?.let {
                    sourceLink {
                        localDirectory.set(projectDir.resolve("src"))
                        remoteUrl.set(it)
                        remoteLineSuffix.set("#L")
                    }
                }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifacts + artifact(javadocJar)
            pom {
                name.set("Test Lib")
                description.set("Playground for gradle and github actions")
                url.set("https://github.com/routis/test-pub")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    connection.set("scm:git:git://git@github.com:routis/test-pub.git")
                    developerConnection.set("scm:git:ssh://git@github.com:routis/test-pub.git")
                    url.set("https://github.com/routis/test-pub")
                }
                developers {
                    developer {
                        organizationUrl.set("https://github.com/routis/")
                    }
                }
            }
        }
    }

    repositories {
        val sonaUri =
            if ((extra["isReleaseVersion"]) as Boolean) {
                "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            } else {
                "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            }

        maven {
            name = "sonatype"
            url = uri(sonaUri)
            credentials(PasswordCredentials::class)
        }
    }
}

signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications["library"])
}
