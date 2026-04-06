plugins {
    kotlin("jvm")
    id("org.kordamp.gradle.jandex") version "2.0.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Quarkus platform
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.15.1"))

    // CDI
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api")
    implementation("io.quarkus:quarkus-core")
    implementation("io.quarkus:quarkus-arc")

    // RabbitMQ messaging
    implementation("io.quarkus:quarkus-messaging-rabbitmq")
    implementation("io.smallrye.reactive:smallrye-reactive-messaging-rabbitmq")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kotlin
    implementation(kotlin("stdlib-jdk8"))

    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation(kotlin("test"))
}

group = "com.simarel.vkbot"
version = "1.0.0-SNAPSHOT"

// Enable JUnit Platform for tests
tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}