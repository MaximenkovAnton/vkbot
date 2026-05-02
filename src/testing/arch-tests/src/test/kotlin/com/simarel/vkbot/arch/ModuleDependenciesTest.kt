package com.simarel.vkbot.arch

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModuleDependenciesTest {

    private lateinit var importedClasses: JavaClasses

    companion object {
        private const val SHARE_PKG = "com.simarel.vkbot.share.."
        private const val INFRASTRUCTURE_PKG = "com.simarel.vkbot.infrastructure.."
        private const val VK_FACADE_PKG = "com.simarel.vkbot.vkFacade.."
        private const val PROCESSOR_PKG = "com.simarel.vkbot.processor.."
        private const val RECEIVER_PKG = "com.simarel.vkbot.receiver.."
        private const val PERSISTENCE_PKG = "com.simarel.vkbot.persistence.."
        private const val AI_PKG = "com.simarel.vkbot.ai.."
        private const val APP_PKG = "com.simarel.vkbot.app.."

        private val ALL_MODULES = listOf(
            INFRASTRUCTURE_PKG,
            VK_FACADE_PKG,
            PROCESSOR_PKG,
            RECEIVER_PKG,
            PERSISTENCE_PKG,
            AI_PKG,
            APP_PKG,
        )

        fun modulesWithoutShare() = ALL_MODULES - SHARE_PKG
        fun modulesWithoutShareAnd(module: String) = modulesWithoutShare() - module
    }

    @BeforeAll
    fun importClasses() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.simarel.vkbot")
    }

    @Test
    fun `share module should not depend on any other module`() {
        noClasses()
            .that()
            .resideInAPackage(SHARE_PKG)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                *modulesWithoutShare().toTypedArray(),
            )
            .check(importedClasses)
    }

    @Test
    fun `infrastructure module should only depend on share`() {
        noClasses()
            .that()
            .resideInAPackage(INFRASTRUCTURE_PKG)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*modulesWithoutShareAnd(INFRASTRUCTURE_PKG).toTypedArray())
            .check(importedClasses)
    }

    @Test
    fun `vk-facade module should only depend on share`() {
        noClasses()
            .that()
            .resideInAPackage(VK_FACADE_PKG)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*modulesWithoutShareAnd(VK_FACADE_PKG).toTypedArray())
            .check(importedClasses)
    }

    @Test
    fun `processor module should only depend on share`() {
        noClasses()
            .that()
            .resideInAPackage(PROCESSOR_PKG)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*modulesWithoutShareAnd(PROCESSOR_PKG).toTypedArray())
            .check(importedClasses)
    }

    @Test
    fun `receiver module should only depend on share`() {
        noClasses()
            .that()
            .resideInAPackage(RECEIVER_PKG)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*modulesWithoutShareAnd(RECEIVER_PKG).toTypedArray())
            .check(importedClasses)
    }

    @Test
    fun `ai module should only depend on share`() {
        noClasses()
            .that()
            .resideInAPackage(AI_PKG)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*modulesWithoutShareAnd(AI_PKG).toTypedArray())
            .check(importedClasses)
    }

    @Test
    fun `persistence module should only depend on share`() {
        noClasses()
            .that()
            .resideInAPackage(PERSISTENCE_PKG)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*modulesWithoutShareAnd(PERSISTENCE_PKG).toTypedArray())
            .check(importedClasses)
    }
}
