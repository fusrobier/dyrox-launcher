import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    manifest {
        attributes("Implementation-Title" to "Dyrox Launcher", "Implementation-Version" to project.version)
    }
}

compose.desktop {
    application {
        mainClass = "net.dyrox.launcher.MainKt"
        // Skia (Compose's renderer) loads native code; JDK 24+ warns unless this is granted.
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            // Windows first; Linux packages are produced by CI on Linux runners.
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Dyrox Launcher"
            packageVersion = "0.1.0"
            vendor = "Dyrox"
            // Modules the jlinked runtime needs beyond what Compose detects automatically.
            modules("java.net.http", "jdk.crypto.ec", "jdk.zipfs")
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                // Keep constant forever: Windows uses it to recognise upgrades of the same app.
                upgradeUuid = "7c1f2b8e-4d3a-4e8b-9a61-2f5d0c9e7b14"
            }
            linux {
                packageName = "dyrox-launcher"
            }
        }
    }
}

// Headless entry point for testing the core without the UI:
//   gradlew :launcher:runCli --args="launch 26.3 --fabric --user Steve"
tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Runs the headless Dyrox dev CLI."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("net.dyrox.launcher.cli.DevCliKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    standardInput = System.`in`
}
