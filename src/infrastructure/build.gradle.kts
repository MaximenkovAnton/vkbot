plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Depend on share module
    implementation(project(":src:share"))
    
    // Quarkus dependencies needed for infrastructure
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.15.1"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-messaging-rabbitmq")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.smallrye.reactive:smallrye-reactive-messaging-rabbitmq")
    
    // Kotlin
    implementation(kotlin("stdlib-jdk8"))
    
    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:latest")
    
    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation(testFixtures(project(":src:testing:test-fixtures")))
    implementation(kotlin("test"))
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