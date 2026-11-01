plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin { jvmToolchain(17) }

application {
    mainClass.set("com.aetheron.backend.ApplicationKt")
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.1.1")
    implementation("io.ktor:ktor-server-body-limit-jvm:3.1.1")
    implementation("io.ktor:ktor-server-cio-jvm:3.1.1")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.1.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.1.1")
}

tasks.test { useJUnitPlatform() }
