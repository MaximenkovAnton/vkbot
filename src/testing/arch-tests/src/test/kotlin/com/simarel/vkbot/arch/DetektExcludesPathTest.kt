package com.simarel.vkbot.arch

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class DetektExcludesPathTest {

    private val projectRoot = Path.of(System.getProperty("project.root") ?: ".")

    private val mainSourceInTestDirectory =
        Path.of("/tmp/test/vk/src/ai/src/main/kotlin/com/simarel/vkbot/AnswerCommand.kt")

    private val mainSourceInNeutralDirectory =
        Path.of("/tmp/work/vk/src/ai/src/main/kotlin/com/simarel/vkbot/AnswerCommand.kt")

    @Test
    fun `detekt excludes do not match main sources when the checkout directory is named test`() {
        val globs = excludesGlobs()
        assertTrue(globs.isNotEmpty()) { "detekt-config.yml has no excludes globs" }

        val offenders = globs.filter {
            globMatches(it, mainSourceInTestDirectory) || globMatches(it, mainSourceInNeutralDirectory)
        }

        assertTrue(offenders.isEmpty()) {
            "detekt-config.yml excludes match main sources: $offenders. " +
                "Anchor test source set globs, for example **/src/test/**."
        }
    }

    private fun excludesGlobs(): List<String> {
        val configuration = Files.readString(projectRoot.resolve(CONFIG_FILE))
        return EXCLUDES_ENTRY.findAll(configuration)
            .flatMap { QUOTED.findAll(it.groupValues[1] + it.groupValues[2]) }
            .map { it.groupValues[2] }
            .toList()
    }

    private fun globMatches(glob: String, path: Path): Boolean = FileSystems.getDefault()
        .getPathMatcher("glob:$glob")
        .matches(path)

    private companion object {
        private const val CONFIG_FILE = "detekt-config.yml"

        private val EXCLUDES_ENTRY = Regex(
            """^[ \t]*excludes:[ \t]*(?:\[([^\]]*)])?[ \t]*((?:\n[ \t]+-[^\n]*)*)""",
            RegexOption.MULTILINE,
        )

        private val QUOTED = Regex("""(['"])([^'"]+)\1""")
    }
}
