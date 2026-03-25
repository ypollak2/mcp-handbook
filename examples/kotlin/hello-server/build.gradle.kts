plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.8.1")
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
