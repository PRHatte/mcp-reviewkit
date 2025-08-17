plugins {
    kotlin("jvm") version "1.9.24"
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.expedia.mcp.reviewkit"
version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    implementation(platform("io.ktor:ktor-bom:2.3.12"))
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-websockets")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-jackson")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")

    implementation("org.slf4j:slf4j-simple:2.0.13")
}

kotlin { jvmToolchain(21) }

application { mainClass.set("com.expedia.mcp.reviewkit.MainKt") }

tasks.withType<Jar> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveFileName.set("app.jar")
    mergeServiceFiles()
}
