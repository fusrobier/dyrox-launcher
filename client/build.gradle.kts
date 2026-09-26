plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // Non-remapping Loom: Minecraft 26.x ships unobfuscated, so code uses Mojang's names directly.
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName.set("dyrox-client")
}

val minecraftVersion: String = providers.gradleProperty("minecraft_version").get()

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    // Provides the Kotlin stdlib, coroutines and kotlinx.serialization at runtime.
    implementation(libs.fabric.language.kotlin)

    // Shared code (IPC client, accounts, palette) is nested inside the mod jar (jar-in-jar).
    implementation(project(":shared"))
    include(project(":shared"))

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    val properties = mapOf("version" to project.version, "minecraft_version" to minecraftVersion)
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
}
