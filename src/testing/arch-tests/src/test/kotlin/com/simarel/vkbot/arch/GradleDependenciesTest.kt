package com.simarel.vkbot.arch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GradleDependenciesTest {

    private val projectRoot = File(System.getProperty("project.root") ?: ".")

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

    private val allowedProductionDependencies = mapOf(
        ":" to emptySet(),
        ":src:share" to emptySet(),
        ":src:receiver" to setOf(":src:share"),
        ":src:processor" to setOf(":src:share"),
        ":src:ai" to setOf(":src:share"),
        ":src:persistence" to setOf(":src:share"),
        ":src:vk-facade" to setOf(":src:share"),
        ":src:infrastructure" to setOf(":src:share"),
        ":src:app" to setOf(
            ":src:ai",
            ":src:share",
            ":src:infrastructure",
            ":src:persistence",
            ":src:vk-facade",
            ":src:processor",
            ":src:receiver",
        ),
        ":src:testing:test-common" to emptySet(),
        ":src:testing:test-fixtures" to setOf(":src:share", ":src:vk-facade"),
        ":src:testing:arch-tests" to setOf(
            ":src:ai",
            ":src:share",
            ":src:infrastructure",
            ":src:vk-facade",
            ":src:processor",
            ":src:receiver",
            ":src:persistence",
            ":src:app",
        ),
    )

    @Test
    fun `build files are discovered`() {
        val buildFiles = findBuildGradleKtsFiles()

        if (buildFiles.isEmpty()) {
            throw AssertionError("No build.gradle.kts files found under ${projectRoot.absolutePath}")
        }
        if (buildFiles.size != 12) {
            throw AssertionError(
                "Expected 12 build.gradle.kts files (root + 11 modules), found ${buildFiles.size}: " +
                    buildFiles.map { it.toProjectPath() }.sorted(),
            )
        }
    }

    @Test
    fun `production project dependencies follow the allowlist`() {
        val violations = mutableListOf<String>()

        for (buildFile in findBuildGradleKtsFiles()) {
            val modulePath = buildFile.toProjectPath()
            val allowed = allowedProductionDependencies[modulePath]
                ?: run {
                    violations.add("$modulePath: module is not part of the allowlist")
                    emptySet()
                }
            for ((configuration, dependency) in productionProjectDependencies(buildFile)) {
                if (dependency !in allowed) {
                    violations.add("$modulePath: $configuration(project(\"$dependency\")) is not allowed")
                }
            }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Production project dependencies outside the allowlist:")
                violations.forEach { appendLine("  - $it") }
                appendLine()
                appendLine("Allowed production dependencies:")
                allowedProductionDependencies.toSortedMap().forEach { (module, dependencies) ->
                    appendLine("  $module -> ${dependencies.sorted()}")
                }
            }
            throw AssertionError(message)
        }
    }

    @Test
    fun `testing modules should only be used in test configurations`() {
        val violations = mutableListOf<String>()

        for (buildFile in findBuildGradleKtsFiles()) {
            violations.addAll(checkBuildFile(buildFile))
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

    private fun findBuildGradleKtsFiles(): List<File> = projectRoot.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".worktrees" && it.name != ".gradle" }
        .filter { it.isFile && it.name == "build.gradle.kts" }
        .sortedBy { it.path }
        .toList()

    private fun File.toProjectPath(): String {
        val normalized = relativeTo(projectRoot).invariantSeparatorsPath
        if (normalized == "build.gradle.kts") {
            return ":"
        }
        val modulePath = normalized.removeSuffix("/build.gradle.kts")
        return ":$modulePath".replace('/', ':')
    }

    private fun productionProjectDependencies(buildFile: File): List<Pair<String, String>> {
        val dependencies = mutableListOf<Pair<String, String>>()
        val block = dependenciesBlock(buildFile.readText()) ?: return dependencies
        val pattern = Regex("""\b(implementation|api|compileOnly|runtimeOnly)\s*\(\s*project\(\s*"(:[^"]+)"\s*\)""")
        for (match in pattern.findAll(block)) {
            dependencies.add(match.groupValues[1] to match.groupValues[2])
        }
        return dependencies
    }

    private fun dependenciesBlock(content: String): String? {
        val start = content.indexOf("dependencies {")
        if (start < 0) {
            return null
        }
        var depth = 0
        for (index in start until content.length) {
            when (content[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return content.substring(start, index + 1)
                    }
                }
            }
        }
        return null
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
