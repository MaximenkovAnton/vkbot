package com.simarel.vkbot.arch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestDataFactoriesTest {

    private val projectRoot = File(System.getProperty("project.root") ?: ".")

    private val sourceRoot = projectRoot.resolve("src")

    private val skippedDirectories = setOf("build", "buildSrc", ".gradle", ".git", ".worktrees", ".idea", ".ast-index")

    private val shareDomainMarkers = listOf(
        "/share/domain/model/",
        "/share/domain/vo/",
        "/share/domain/event/",
    )

    private val declarationPattern = Regex(
        """^\s*(?:@[\w.]+(?:\s*\(.*?\))?\s+)*((?:\w+\s+)*)(class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)""",
    )

    private val createFunctionPattern = Regex("""\bfun\s+(?:<.*?>\s*)?(create[A-Za-z0-9_]*)\s*(\()""")

    @Test
    fun `test sources create test data only through test data providers`() {
        val violations = directCreationViolations(forbiddenTestDataTypes())
        assertNoViolations(
            violations = violations,
            description = "Test data created without a test data provider",
            hint = "Use Fake*Provider from src/testing/test-fixtures or module src/testFixtures.",
        )
    }

    @Test
    fun `every factory method parameter has a default`() {
        val violations = factorySourceFiles().flatMap(::parameterViolations)
        assertNoViolations(
            violations = violations,
            description = "Factory methods with parameters without a default",
            hint = "Add '= <realistic default>' to every parameter of a create* factory method.",
        )
    }

    private fun assertNoViolations(violations: List<String>, description: String, hint: String) {
        if (violations.isEmpty()) return
        throw AssertionError(
            buildString {
                appendLine("$description: ${violations.size}")
                violations.forEach { appendLine("  $it") }
                appendLine()
                appendLine(hint)
            },
        )
    }

    private fun forbiddenTestDataTypes(): Map<String, Set<String>> {
        val forbidden = mutableListOf<ForbiddenType>()
        for (file in mainSourceFiles()) {
            val relativePath = file.relativePath()
            val declarations = declarations(file)
            val dataTypes = when {
                relativePath.isShareDomainFile() -> declarations.filter { it.isPlainClass() }
                relativePath.isPortOrCommandFile() -> declarations.filter { it.isContractType() }
                relativePath.isDtoFile() -> declarations.filter { it.isPlainClass() }
                else -> emptyList()
            }
            forbidden += dataTypes.map { it.toForbidden() }
        }
        return forbidden.groupBy { it.name }.mapValues { (_, types) -> types.map { it.owner }.toSet() }
    }

    private fun directCreationViolations(
        forbidden: Map<String, Set<String>>,
    ): List<String> = testSourceFiles().flatMap { file -> directCreationViolations(file, forbidden) }

    private fun directCreationViolations(file: File, forbidden: Map<String, Set<String>>): List<String> {
        val baseName = file.nameWithoutExtension
        val lines = file.readLines()
        return forbidden
            .filterKeys { typeName -> !isOwnTest(baseName, typeName, forbidden.getValue(typeName)) }
            .flatMap { (typeName, _) -> directCreationLines(file, lines, typeName) }
    }

    private fun isOwnTest(
        baseName: String,
        typeName: String,
        owners: Set<String>,
    ): Boolean = baseName == "${typeName}Test" || owners.any { baseName == "${it}Test" }

    private fun directCreationLines(file: File, lines: List<String>, typeName: String): List<String> {
        val patterns = listOf(
            Regex("""\b${Regex.escape(typeName)}\s*\("""),
            Regex("""\b${Regex.escape(typeName)}\s*\.of\s*\("""),
        )
        return lines.mapIndexedNotNull { index, line ->
            if (patterns.any { it.containsMatchIn(line) }) directCreationMessage(file, index + 1, typeName) else null
        }
    }

    private fun directCreationMessage(
        file: File,
        line: Int,
        typeName: String,
    ): String = "${file.relativePath()}:$line: $typeName создаётся напрямую; " +
        "используй Fake*Provider из src/testing/test-fixtures или модульных src/testFixtures"

    private fun parameterViolations(file: File): List<String> {
        val text = file.readText()
        val relativePath = file.relativePath()
        return createFunctionPattern.findAll(text).flatMap { match ->
            parameterViolations(text, relativePath, match)
        }.toList()
    }

    private fun parameterViolations(text: String, relativePath: String, match: MatchResult): Sequence<String> {
        val functionName = match.groupValues[1]
        val open = match.range.last
        val close = matchingParen(text, open) ?: return emptySequence()
        return parameters(text, open + 1, close).asSequence().mapNotNull { parameter ->
            parameter.name?.let { name ->
                "$relativePath:${lineAt(text, parameter.startIndex)}: параметр $name функции $functionName " +
                    "без дефолта; тест будет обязан знать о поле"
            }
        }
    }

    private fun matchingParen(text: String, openIndex: Int): Int? {
        var depth = 0
        for (index in openIndex until text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun parameters(text: String, startIndex: Int, endIndex: Int): List<Parameter> {
        val result = mutableListOf<Parameter>()
        var bracketDepth = 0
        var angleDepth = 0
        var segmentStart = startIndex
        for (index in startIndex until endIndex) {
            val char = text[index]
            when {
                char == ',' && bracketDepth == 0 && angleDepth == 0 -> {
                    result.add(parameter(text, segmentStart, index))
                    segmentStart = index + 1
                }

                char in "([{" -> bracketDepth++
                char in ")]}" -> bracketDepth--
                char == '<' -> angleDepth++
                char == '>' && text.getOrNull(index - 1) != '-' -> angleDepth--
            }
        }
        result.add(parameter(text, segmentStart, endIndex))
        return result.filter { it.name != null }
    }

    private fun parameter(text: String, startIndex: Int, endIndex: Int): Parameter {
        val segment = text.substring(startIndex, endIndex)
        val trimmed = segment.trim()
        if (trimmed.isEmpty() || '=' in trimmed || ':' !in trimmed) {
            return Parameter(null, startIndex)
        }
        val name = trimmed.substringBefore(':').trim()
        return Parameter(name.ifEmpty { null }, startIndex + segment.indexOf(trimmed))
    }

    private fun lineAt(text: String, index: Int): Int = text.substring(0, index).count { it == '\n' } + 1

    private fun declarations(file: File): List<Declaration> {
        val stack = ArrayDeque<Pair<Int, Declaration>>()
        val result = mutableListOf<Declaration>()
        for (line in file.readLines()) {
            val match = declarationPattern.find(line) ?: continue
            val indent = line.indexOfFirst { !it.isWhitespace() }
            while (stack.isNotEmpty() && stack.last().first >= indent) {
                stack.removeLast()
            }
            val declaration = Declaration(
                name = match.groupValues[3],
                kind = match.groupValues[2],
                modifiers = match.groupValues[1],
                parent = stack.lastOrNull()?.second,
            )
            result.add(declaration)
            stack.addLast(indent to declaration)
        }
        return result
    }

    private fun mainSourceFiles(): List<File> = kotlinFiles(sourceRoot).filter { it.relativePath().isMainSource() }

    private fun testSourceFiles(): List<File> {
        val modules = sourceRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        return modules.map { it.resolve("src/test") }.filter { it.isDirectory }.flatMap(::kotlinFiles)
    }

    private fun factorySourceFiles(): List<File> {
        val modules = sourceRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        val moduleFixtures = modules.map { it.resolve("src/testFixtures") }.filter { it.isDirectory }
        return (moduleFixtures + sourceRoot.resolve("testing/test-fixtures/src/main"))
            .filter { it.isDirectory }
            .flatMap(::kotlinFiles)
    }

    private fun kotlinFiles(root: File): List<File> = root.walkTopDown()
        .onEnter { it.name !in skippedDirectories }
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun File.relativePath(): String = relativeTo(projectRoot).invariantSeparatorsPath

    private fun Declaration.isPlainClass(): Boolean = kind == "class" && !isEnum

    private fun Declaration.isContractType(): Boolean = isPlainClass() && isContractBranch()

    private fun Declaration.isContractBranch(): Boolean = isContractName || (parent?.isContractBranch() == true)

    private fun Declaration.toForbidden(): ForbiddenType = ForbiddenType(name = name, owner = rootName)

    private fun String.isMainSource(): Boolean = contains("/src/main/kotlin/")

    private fun String.isShareDomainFile(): Boolean = shareDomainMarkers.any { contains(it) }

    private fun String.isPortOrCommandFile(): Boolean = contains("/port/") || contains("/command/")

    private fun String.isDtoFile(): Boolean = contains("/adapter/output/") && contains("/dto/")

    private data class ForbiddenType(val name: String, val owner: String)

    private data class Parameter(val name: String?, val startIndex: Int)

    private data class Declaration(
        val name: String,
        val kind: String,
        val modifiers: String,
        val parent: Declaration?,
    ) {
        val isEnum: Boolean get() = modifiers.split(' ').contains("enum")
        val isContractName: Boolean get() = name.endsWith("Request") || name.endsWith("Response")
        val rootName: String get() = parent?.rootName ?: name
    }
}
