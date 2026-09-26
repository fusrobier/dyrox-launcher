import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    // Declared once here so every subproject shares one plugin classloader.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.fabric.loom) apply false
}

allprojects {
    group = providers.gradleProperty("project_group").get()
    version = providers.gradleProperty("project_version").get()
}

subprojects {
    // Reproducible archives: identical inputs produce byte-identical jars.
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    // Everything compiles with JDK 25. :shared and :launcher emit Java 21 bytecode so :shared stays
    // loadable by Minecraft 1.21.x (Java 21); :client targets 25 because it links against 26.3's classes.
    val javaRelease = if (name == "client") 25 else 21
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(javaRelease.toString()))
                freeCompilerArgs.add("-Xjdk-release=$javaRelease")
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(javaRelease)
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}
