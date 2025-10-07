pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    val detektPluginVersion: String by settings
    val detektPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
        id(detektPluginId) version detektPluginVersion
        kotlin("jvm") version "2.2.20" // todo: move to 2.3.0 after release for jdk 25 support
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "vkbot"
