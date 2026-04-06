plugins {
    kotlin("jvm")
    `java-test-fixtures`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Depend on share and vk-facade modules
    implementation(project(":src:modules:share"))
    implementation(project(":src:modules:vk-facade"))

    // Quarkus platform for version management
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.15.1"))

    // CDI
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api")
    implementation("io.quarkus:quarkus-arc")

    // Kotlin
    implementation(kotlin("stdlib-jdk8"))

    // Jackson for test fixtures
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
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