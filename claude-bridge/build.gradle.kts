plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.gradleup.shadow")
    application
}

application {
    mainClass.set("com.dc.murmur.bridge.MainKt")
}

val ktorVersion = "3.0.3"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

tasks.shadowJar {
    archiveBaseName.set("claude-bridge")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
}

kotlin {
    jvmToolchain(11)
}
