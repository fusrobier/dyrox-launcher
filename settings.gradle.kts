pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
    }
}

plugins {
    // Lets Gradle download the JDK toolchain (Java 25) if it is not installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Compose Multiplatform depends on a few androidx artifacts that only live on Google's Maven.
        google()
    }
}

rootProject.name = "dyrox"

include(":shared", ":launcher")
