plugins {
    kotlin("jvm")
    id("io.quarkus")
    id("org.kordamp.gradle.jandex") version "2.3.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusLangChain4jVersion: String by project

dependencies {
    // Depend on all application modules
    implementation(project(":src:ai"))
    implementation(project(":src:share"))
    implementation(project(":src:infrastructure"))
    implementation(project(":src:persistence"))
    implementation(project(":src:vk-facade"))
    implementation(project(":src:processor"))
    implementation(project(":src:receiver"))

    // Quarkus platform
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.34.2"))

    // Core Quarkus dependencies
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-core")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-rest")

    // LangChain4j (via ai module)
    implementation("io.quarkiverse.langchain4j:quarkus-langchain4j-ollama:$quarkusLangChain4jVersion")

    // Kotlin
    implementation(kotlin("stdlib-jdk8"))

    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":src:testing:test-fixtures")))
    testImplementation(project(":src:testing:test-common"))

    // WireMock for HTTP mocking
    testImplementation("org.wiremock:wiremock-standalone:3.9.1")

    // Awaitility for async test assertions
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
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
