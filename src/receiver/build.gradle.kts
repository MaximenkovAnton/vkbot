plugins {
    kotlin("jvm")
    `java-test-fixtures`
    id("org.kordamp.gradle.jandex") version "2.3.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":src:share"))

    // Test fixtures need access to shared test-fixtures
    testFixturesImplementation(project(":src:share"))
    testFixturesImplementation(testFixtures(project(":src:testing:test-fixtures")))

    // Jakarta JSON API for test fixtures
    testFixturesImplementation("jakarta.json:jakarta.json-api:2.1.3")
    testFixturesImplementation("org.eclipse.parsson:parsson:1.1.5")

    // Test dependencies
    testImplementation(testFixtures(project))  // Own testFixtures
    testImplementation(testFixtures(project(":src:testing:test-fixtures")))  // Shared test-fixtures

    // Quarkus dependencies
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.34.2"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-messaging-rabbitmq")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.smallrye.reactive:smallrye-reactive-messaging-rabbitmq")

    // Kotlin
    implementation(kotlin("stdlib-jdk8"))

    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:latest")

    // Jakarta JSON API
    implementation("jakarta.json:jakarta.json-api:2.1.3")

    // Testing - infrastructure needed for CDI decorators in tests
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation(kotlin("test"))
    testImplementation(project(":src:infrastructure"))
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

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
