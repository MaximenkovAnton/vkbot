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
        kotlin("jvm") version "2.3.0"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "vkbot"

include("src:modules:infrastructure")
include("src:modules:processor")
include("src:modules:share")
include("src:modules:testing:test-fixtures")
include("src:modules:vk-facade")
