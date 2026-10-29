plugins {
    base
    jacoco
    kotlin("jvm") apply false
    id("io.gitlab.arturbosch.detekt") apply false
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0" apply false
    id("org.kordamp.gradle.jandex") version "2.3.0" apply false
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

repositories {
    mavenCentral()
    mavenLocal()
}

val measuredLineCoverage = 0.5043
val coverageMin = 0.40

val coverageModules = listOf(
    "src:ai",
    "src:app",
    "src:infrastructure",
    "src:persistence",
    "src:processor",
    "src:receiver",
    "src:share",
    "src:vk-facade",
)

subprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "jacoco")

    plugins.withId("java") {
        dependencies {
            add(
                "implementation",
                enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"),
            )
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        autoCorrect = false
        config.setFrom(rootProject.files("detekt-config.yml"))
        reports {
            xml.required.set(false)
            html.required.set(true)
            txt.required.set(false)
        }
        val moduleBaseline = file("${projectDir}/detekt-baseline.xml")
        if (moduleBaseline.exists()) {
            baseline = moduleBaseline
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        failOnNoDiscoveredTests = false
    }
}

val coveredProjects = coverageModules.map { project(":$it") }

val coverageReport = tasks.register<org.gradle.testing.jacoco.tasks.JacocoReport>("coverageReport") {
    group = "verification"
    description = "Aggregated JaCoCo line coverage report for the deployable modules."
    dependsOn(coveredProjects.map { "${it.path}:test" })
    executionData.setFrom(coveredProjects.map { it.layout.buildDirectory.file("jacoco/test.exec") })
    classDirectories.setFrom(
        coveredProjects.map {
            files(
                it.layout.buildDirectory.dir("classes/kotlin/main"),
                it.layout.buildDirectory.dir("classes/java/main"),
            )
        },
    )
    sourceDirectories.setFrom(coveredProjects.map { it.layout.projectDirectory.dir("src/main/kotlin") })
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

val coverageVerification = tasks.register<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>(
    "coverageVerification",
) {
    group = "verification"
    description = "Fails the build when aggregated line coverage drops below $coverageMin (measured $measuredLineCoverage)."
    dependsOn(coverageReport)
    executionData.setFrom(coveredProjects.map { it.layout.buildDirectory.file("jacoco/test.exec") })
    classDirectories.setFrom(
        coveredProjects.map {
            files(
                it.layout.buildDirectory.dir("classes/kotlin/main"),
                it.layout.buildDirectory.dir("classes/java/main"),
            )
        },
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = coverageMin.toBigDecimal()
            }
        }
    }
}

val capabilitiesCheck = tasks.register<Exec>("capabilitiesCheck") {
    group = "verification"
    description = "Fails when docs/capabilities references a path, a test or an ADR id that no longer exists."
    commandLine("bash", "scripts/check-capabilities.sh")
}

tasks.named("check") {
    dependsOn(coverageVerification)
    dependsOn(capabilitiesCheck)
}
