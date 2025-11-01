plugins {
    java
    id("io.quarkus")
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val quarkusLangChain4jVersion: String by project
val detektPluginVersion: String by project

dependencies {
    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))
    implementation("io.quarkus:quarkus-agroal")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-flyway")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-jacoco")
    implementation("io.quarkus:quarkus-info")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-messaging-rabbitmq")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-observability-devservices")
    implementation("io.quarkus:quarkus-observability-devservices-lgtm")
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-spring-data-jpa")
    implementation("io.quarkiverse.langchain4j:quarkus-langchain4j-ollama:$quarkusLangChain4jVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-logging")

    //  Additional
    // jackson kotlin module for json serialization/deserialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:latest")

    // detekt
    detektPlugins("com.wolt.arrow.detekt:rules:0.5.0")
    detekt("io.gitlab.arturbosch.detekt:detekt-formatting:$detektPluginVersion")
    detekt("io.gitlab.arturbosch.detekt:detekt-cli:$detektPluginVersion")
    // testing dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-component")
    testImplementation("io.rest-assured:rest-assured")
    implementation(kotlin("stdlib-jdk8"))
}

group = "com.simarel.vkbot"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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
    // update detekt to move from 23+
    // update kotlin jvm plugin to move to 25+
}

detekt {
    config = files("detekt-config.yml")
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = true
    reports {
        xml.enabled = false
        html.enabled = true
        txt.enabled = false
    }
}
