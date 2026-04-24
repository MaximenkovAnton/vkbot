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

    // Test fixtures need access to share module classes
    testFixturesImplementation(project(":src:share"))
    testFixturesImplementation(testFixtures(project(":src:testing:test-fixtures")))

    // Test dependencies
    testImplementation(testFixtures(project))
    testImplementation(testFixtures(project(":src:testing:test-fixtures")))

    // Quarkus platform for version management
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.34.2"))

    // CDI
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api")
    implementation("io.quarkus:quarkus-arc")

    // Hibernate ORM with Panache
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-hibernate-orm")

    // Liquibase for migrations
    implementation("io.quarkus:quarkus-liquibase")

    // PostgreSQL driver
    implementation("io.quarkus:quarkus-jdbc-postgresql")

    // Kotlin
    implementation(kotlin("stdlib-jdk8"))

    // Jackson for JSON handling
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Testing
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
