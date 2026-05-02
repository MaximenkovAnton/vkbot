plugins {
    kotlin("jvm")
    id("org.kordamp.gradle.jandex") version "2.3.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusLangChain4jVersion: String by project

dependencies {
    implementation(project(":src:share"))
    implementation(project(":src:persistence"))

    // Quarkus platform for version management
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.34.2"))

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
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
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
