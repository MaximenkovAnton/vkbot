plugins {
    kotlin("jvm")
    `java-test-fixtures`
    id("org.kordamp.gradle.jandex") version "2.0.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":src:share"))

    // Test fixtures need access to share module classes and shared test-fixtures
    testFixturesImplementation(project(":src:share"))
    testFixturesImplementation(testFixtures(project(":src:testing:test-fixtures")))

    // Test fixtures (own testFixtures + shared test-fixtures for FakePublishEventCommand, FakeVoProvider)
    testImplementation(testFixtures(project))  // Own testFixtures
    testImplementation(testFixtures(project(":src:testing:test-fixtures")))  // Shared test-fixtures

    // Quarkus platform for version management
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.34.2"))

    // CDI
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api")
    implementation("io.quarkus:quarkus-arc")

    // Kotlin
    implementation(kotlin("stdlib-jdk8"))

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // LangChain4j
    implementation("io.quarkiverse.langchain4j:quarkus-langchain4j-ollama:0.25.0")

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
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}
