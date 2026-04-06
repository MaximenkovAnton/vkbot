plugins {
    kotlin("jvm")
    `java-test-fixtures`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Depend on share module
    implementation(project(":modules:share"))
    
    // Kotlin
    implementation(kotlin("stdlib-jdk8"))
    
    // Jackson for test fixtures
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    
    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    implementation(kotlin("test"))
}

group = "com.simarel.vkbot"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}