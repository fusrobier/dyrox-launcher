import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    // Declared once here so every subproject shares one plugin classloader.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
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

    // :shared and :launcher compile with JDK 25 but emit Java 21 bytecode, so :shared
    // stays loadable by older Minecraft versions (1.21.x runs on Java 21).
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
                freeCompilerArgs.add("-Xjdk-release=21")
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(21)
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
