package com.simarel.vkbot.arch

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NamingConventionsTest {

    private lateinit var importedClasses: JavaClasses

    @BeforeAll
    fun importClasses() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE)
    }

    @Test
    fun `L7 types are named after their responsibility, not after a layer`() {
        ArchRuleDefinition.classes()
            .should(notBeNamedAfterALayer())
            .because(
                "имя слоя (*Controller, *Service, *Repository) описывает место в архитектуре, " +
                    "а не работу класса: в такой класс складывают транспорт, правила и SQL сразу, " +
                    "и он превращается в свалку. SRP, ISP и тестируемость держатся на классе " +
                    "с одной ответственностью и честным именем " +
                    "(docs/architecture/patterns.md, ADR 2026-11-13-responsibility-type-names)",
            )
            .check(importedClasses)
    }

    @Test
    fun `naming rule sees production types from every module`() {
        val missing = MODULE_PACKAGES.filter { modulePackage ->
            importedClasses.none { javaClass -> javaClass.packageName.startsWith(modulePackage) }
        }

        assertTrue(missing.isEmpty()) {
            "NamingConventionsTest не видит production-типы модулей $missing: правило L7 было бы вакуумным"
        }
    }

    private fun notBeNamedAfterALayer(): ArchCondition<JavaClass> = object : ArchCondition<JavaClass>(
        "be named after their responsibility, not after a layer",
    ) {
        override fun check(item: JavaClass, events: ConditionEvents) {
            val suffix = BANNED_SUFFIXES.firstOrNull { item.simpleName.endsWith(it) } ?: return
            events.add(
                SimpleConditionEvent.violated(
                    item,
                    "${item.name} оканчивается на '$suffix': это имя слоя, а не ответственности. " +
                        "Назови класс по работе, которую он делает: XxxUsecase, XxxAdapter, XxxCommand, " +
                        "XxxTable, XxxReader, XxxWriter, XxxGenerator, XxxFormatter, XxxPolicy, XxxMapper. " +
                        "Если у класса нет одной ответственности — раздели его на операции за портами " +
                        "(один OutputPort<REQ, RESP> — одна операция).",
                ),
            )
        }
    }

    private companion object {
        private const val ROOT_PACKAGE = "com.simarel.vkbot"

        private val BANNED_SUFFIXES = listOf("Controller", "Service", "Repository")

        private val MODULE_PACKAGES = listOf(
            "com.simarel.vkbot.share",
            "com.simarel.vkbot.receiver",
            "com.simarel.vkbot.persistence",
            "com.simarel.vkbot.processor",
            "com.simarel.vkbot.ai",
            "com.simarel.vkbot.vkFacade",
            "com.simarel.vkbot.infrastructure",
            "com.simarel.vkbot.app",
            "com.simarel.vkbot.greetings",
        )
    }
}
