import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

group = "io.github.seky443"
version = "0.1.0"

// The library is Kotlin stdlib only. Java 11 bytecode keeps it consumable from an Android module
// with minSdk 24 without desugaring.
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // The public surface is an API contract; make the compiler enforce it.
        explicitApi()
    }
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    testImplementation(libs.junit)
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("sstv-encoder")
                description.set(
                    "A dependency-free Kotlin encoder that turns an image into a Slow-Scan " +
                        "Television (SSTV) audio transmission."
                )
                url.set("https://github.com/SEKY443/sstv-encoder")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("SEKY443")
                        name.set("SEKY443")
                        url.set("https://github.com/SEKY443")
                    }
                }
                scm {
                    url.set("https://github.com/SEKY443/sstv-encoder")
                    connection.set("scm:git:https://github.com/SEKY443/sstv-encoder.git")
                    developerConnection.set("scm:git:ssh://git@github.com/SEKY443/sstv-encoder.git")
                }
            }
        }
    }
}
