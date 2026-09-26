plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Deliberately tiny dependency surface: this module is bundled into the Fabric mod (Phase 5),
// where coroutines and serialization are provided by fabric-language-kotlin at the same versions.
dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    // Windows DPAPI for the account vault. Minecraft ships JNA; the launcher adds it itself.
    compileOnly(libs.jna.platform)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.jna.platform)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
