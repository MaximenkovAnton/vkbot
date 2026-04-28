plugins {
    kotlin("jvm")
    `java-test-fixtures`
    id("nu.studer.jooq") version "9.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":src:share"))
    implementation(project(":src:vk-facade"))

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

    // Quarkus for CDI and jandex
    implementation("io.quarkus:quarkus-core")
    implementation("io.quarkus:quarkus-arc")

    // Agroal connection pool
    implementation("io.quarkus:quarkus-agroal")

    // jOOQ for type-safe SQL
    implementation("org.jooq:jooq:3.19.10")

    // CDI annotations
    implementation("jakarta.inject:jakarta.inject-api")

    // Liquibase for migrations
    implementation("io.quarkus:quarkus-liquibase")

    // jOOQ code generation
    jooqGenerator("org.jooq:jooq-meta:3.19.10")
    jooqGenerator("org.jooq:jooq-codegen:3.19.10")
    jooqGenerator("org.postgresql:postgresql:42.7.3")

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

// jOOQ code generation - disabled for now, using manual table constants
// Run with DB available to generate: ./gradlew generateJooq
// Or set ENV vars: JOOQ_JDBC_URL, JOOQ_JDBC_USER, JOOQ_JDBC_PASSWORD
jooq {
    version.set("3.19.10")

    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = System.getenv("JOOQ_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/vkbot"
                    user = System.getenv("JOOQ_JDBC_USER") ?: "vkbot"
                    password = System.getenv("JOOQ_JDBC_PASSWORD") ?: "vkbot"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        includes = "messages|vk_user_profiles|vk_group_profiles"
                    }
                    target.apply {
                        packageName = "com.simarel.vkbot.persistence.generated"
                        directory = "src/main/kotlin"
                    }
                    strategy.apply {
                        name = "org.jooq.codegen.DefaultGeneratorStrategy"
                    }
                }
            }
        }
    }
}

// Disable automatic jOOQ generation during build
tasks.withType<nu.studer.gradle.jooq.JooqGenerate> {
    onlyIf { System.getenv("JOOQ_JDBC_URL") != null }
}
