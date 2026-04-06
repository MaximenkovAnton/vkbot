plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.15.1"))

    implementation(project(":src:share"))
    implementation(project(":src:infrastructure"))
    implementation(project(":src:vk-facade"))
    implementation(project(":src:processor"))
    implementation(project(":src:receiver"))

    implementation(kotlin("stdlib-jdk8"))

    implementation("org.junit.jupiter:junit-jupiter:5.10.3")
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
    useJUnitPlatform()
}
