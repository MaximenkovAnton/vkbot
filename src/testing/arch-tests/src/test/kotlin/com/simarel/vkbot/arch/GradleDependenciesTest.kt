package com.simarel.vkbot.arch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GradleDependenciesTest {

    private val projectRoot = File(".")

    private val testOnlyConfigurations = setOf(
        "testImplementation",
        "testFixturesImplementation",
        "testRuntimeOnly",
        "testCompileOnly",
        "testAnnotationProcessor",
    )

    private val productionConfigurations = setOf(
        "implementation",
        "api",
        "compileOnly",
        "runtimeOnly",
        "annotationProcessor",
        "kapt",
    )

    private val testingModules = setOf(
        ":src:testing:test-common",
        ":src:testing:test-fixtures",
        ":src:testing:arch-tests",
    )

    @Test
    fun `testing modules should only be used in test configurations`() {
        val violations = mutableListOf<String>()
        val buildFiles = findBuildGradleKtsFiles()

        for (buildFile in buildFiles) {
            val violationsInFile = checkBuildFile(buildFile)
            violations.addAll(violationsInFile)
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Testing modules used in production configurations:")
                violations.forEach { appendLine("  - $it") }
                appendLine()
                appendLine("Testing modules can only be used in these configurations:")
                testOnlyConfigurations.forEach { appendLine("  - $it") }
            }
            throw AssertionError(message)
        }
    }

    private fun findBuildGradleKtsFiles(): List<File> {
        val buildFiles = mutableListOf<File>()

        projectRoot.walkTopDown()
            .filter { it.name == "build.gradle.kts" }
            .filter { !it.path.contains("build") }
            .forEach { buildFiles.add(it) }

        return buildFiles
    }

    private fun checkBuildFile(buildFile: File): List<String> {
        val violations = mutableListOf<String>()
        val content = buildFile.readText()
        val lines = content.lines()

        var inDependenciesBlock = false
        var braceDepth = 0

        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()

            if (trimmedLine.startsWith("dependencies {")) {
                inDependenciesBlock = true
                braceDepth = 1
                continue
            }

            if (inDependenciesBlock) {
                braceDepth += line.count { it == '{' } - line.count { it == '}' }

                if (braceDepth <= 0) {
                    inDependenciesBlock = false
                    continue
                }

                val violation = checkDependencyLine(trimmedLine, buildFile, index + 1)
                if (violation != null) {
                    violations.add(violation)
                }
            }
        }

        return violations
    }

    private fun checkDependencyLine(line: String, buildFile: File, lineNumber: Int): String? {
        if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*") || line.isBlank()) {
            return null
        }

        for (prodConfig in productionConfigurations) {
            if (line.contains("$prodConfig(") || line.contains("$prodConfig ")) {
                for (testingModule in testingModules) {
                    if (line.contains(testingModule)) {
                        return "${buildFile.path}:$lineNumber: $prodConfig($testingModule)"
                    }
                }
            }
        }

        return null
    }
}
