plugins {
    // Lets Gradle download the Java 17 toolchain itself, so a clone builds on any machine
    // regardless of which JDK happens to be installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "sstv-encoder"