plugins {
    kotlin("jvm")
    `java-test-fixtures`
    id("org.kordamp.gradle.jandex") version "2.3.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusLangChain4jVersion: String by project

dependencies {
    implementation(project(":src:share"))

    // Test fixtures need access to share module classes and shared test-fixtures
    testFixturesImplementation(project(":src:share"))
    testFixturesImplementation(testFixtures(project(":src:testing:test-fixtures")))

    // Test fixtures (own testFixtures + shared test-fixtures for FakeVoProvider, FakeModelProvider)
    testImplementation(testFixtures(project))
    testImplementation(testFixtures(project(":src:testing:test-fixtures")))

    // Quarkus platform for version management

    // CDI
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api")
    implementation("io.quarkus:quarkus-arc")

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // REST Client
    implementation("io.quarkus:quarkus-rest-client-jackson")

    // Kotlin
    implementation(kotlin("stdlib-jdk8"))

    // LangChain4j
    implementation("io.quarkiverse.langchain4j:quarkus-langchain4j-ollama:$quarkusLangChain4jVersion")

    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation(kotlin("test"))
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
