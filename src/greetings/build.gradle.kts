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

    testFixturesImplementation(project(":src:share"))
    testFixturesImplementation(testFixtures(project(":src:testing:test-fixtures")))

    testImplementation(testFixtures(project))
    testImplementation(testFixtures(project(":src:testing:test-fixtures")))

    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.opentelemetry:opentelemetry-api")

    implementation(kotlin("stdlib-jdk8"))

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
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
