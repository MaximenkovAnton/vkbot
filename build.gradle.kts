plugins {
    java
    id("io.quarkus")
    kotlin("jvm")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val quarkusLangChain4jVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-observability-devservices")
    implementation("io.quarkus:quarkus-flyway")
    implementation("io.quarkus:quarkus-info")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkus:quarkus-jacoco")
    implementation("io.quarkus:quarkus-agroal")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-spring-data-jpa")
    implementation("io.quarkus:quarkus-reactive-routes")
    implementation("io.quarkus:quarkus-reactive-routes")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkiverse.langchain4j:quarkus-langchain4j-ollama:${quarkusLangChain4jVersion}")
    testImplementation("io.quarkus:quarkus-junit5")
    implementation(kotlin("stdlib-jdk8"))
}

group = "com.simarel"
version = "1.0.0-SNAPSHOT"

java {
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
kotlin {
    jvmToolchain(21)
}