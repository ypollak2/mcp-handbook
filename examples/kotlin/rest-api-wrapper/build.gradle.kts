plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.8.1")
    implementation("io.ktor:ktor-client-cio:3.2.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

application {
    mainClass.set("com.example.mcp.MainKt")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        javaParameters = true
    }
}
