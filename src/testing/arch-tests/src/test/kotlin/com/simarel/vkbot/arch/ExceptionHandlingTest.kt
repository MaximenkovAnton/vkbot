@file:Suppress("MaxLineLength")

package com.simarel.vkbot.arch

import com.simarel.vkbot.share.domain.exception.VkBotAppException
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaConstructorCall
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExceptionHandlingTest {

    private val projectRoot = File(System.getProperty("project.root") ?: ".")

    private val appExceptionName = VkBotAppException::class.java.simpleName

    private val requireCall = Regex("""\brequire(?:NotNull)?\s*\(""")

    private lateinit var importedClasses: JavaClasses

    private companion object {
        private val BASE_EXCEPTION_TYPES = listOf(
            RuntimeException::class.java,
            Exception::class.java,
            Throwable::class.java,
        )

        private const val NO_WHEN_BRANCH_MATCHED_EXCEPTION = "kotlin.NoWhenBranchMatchedException"
        private const val UNSUPPORTED_OPERATION_EXCEPTION = "java.lang.UnsupportedOperationException"
        private const val DEFAULT_ARGUMENTS_SUFFIX = "\$default"
    }

    @BeforeAll
    fun importClasses() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.simarel.vkbot")
    }

    @Test
    fun `only VkBotAppException and its subclasses are thrown`() {
        ArchRuleDefinition.classes()
            .should(throwOnlyAppExceptions())
            .because(
                "one exception hierarchy lets callers classify, log and translate failures; " +
                    "raw RuntimeException, IllegalStateException and IllegalArgumentException carry no domain meaning",
            )
            .check(importedClasses)
    }

    @Test
    fun `state checks do not use require`() {
        val violations = mainKotlinSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (requireCall.containsMatchIn(line)) {
                    "${file.relativePath()}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }

        assertTrue(violations.isEmpty()) {
            buildString {
                appendLine(
                    "require/requireNotNull бросают общий IllegalArgumentException и скрывают, " +
                        "что именно и где не прошло проверку:",
                )
                violations.forEach { appendLine("  $it") }
                appendLine()
                appendLine(
                    "Бросай конкретное исключение иерархии " +
                        "$appExceptionName -> ValidationException -> " +
                        "<ValueObject>ValidationException -> <проверка>Exception",
                )
            }
        }
    }

    private fun throwOnlyAppExceptions(): ArchCondition<JavaClass> = object : ArchCondition<JavaClass>("throw only $appExceptionName and its subclasses") {
        override fun check(item: JavaClass, events: ConditionEvents) {
            item.constructorCallsFromSelf
                .filter { it.constructsThrowable() }
                .filterNot { it.isAllowedThrow() }
                .forEach { call -> events.add(SimpleConditionEvent.violated(item, violation(call))) }
        }
    }

    private fun JavaConstructorCall.constructsThrowable(): Boolean = targetOwner.isAssignableTo(Throwable::class.java)

    private fun JavaConstructorCall.isAllowedThrow(): Boolean = targetOwner.isAssignableTo(VkBotAppException::class.java) ||
        isAppExceptionBaseConstructor() ||
        isCompilerGenerated()

    private fun JavaConstructorCall.isAppExceptionBaseConstructor(): Boolean = originOwner.isAssignableTo(VkBotAppException::class.java) &&
        BASE_EXCEPTION_TYPES.any { targetOwner.isEquivalentTo(it) }

    private fun JavaConstructorCall.isCompilerGenerated(): Boolean = targetOwner.name == NO_WHEN_BRANCH_MATCHED_EXCEPTION ||
        (
            targetOwner.name == UNSUPPORTED_OPERATION_EXCEPTION &&
                origin.name.endsWith(DEFAULT_ARGUMENTS_SUFFIX)
            )

    private fun violation(call: JavaConstructorCall): String = "${call.sourceCodeLocation} constructs ${call.targetOwner.name} in ${call.origin.fullName}; " +
        "throw a $appExceptionName subclass instead"

    private fun mainKotlinSources(): List<File> = projectRoot.resolve("src").walkTopDown()
        .onEnter { it.name != "build" && it.name != ".gradle" && it.name != ".worktrees" }
        .filter { file ->
            file.isFile &&
                file.extension == "kt" &&
                file.invariantSeparatorsPath.contains("/src/main/kotlin/")
        }
        .toList()

    private fun File.relativePath(): String = relativeTo(projectRoot).invariantSeparatorsPath
}
