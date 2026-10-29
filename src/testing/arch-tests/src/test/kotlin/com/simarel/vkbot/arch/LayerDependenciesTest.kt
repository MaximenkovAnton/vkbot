package com.simarel.vkbot.arch

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LayerDependenciesTest {

    private lateinit var importedClasses: JavaClasses

    companion object {
        private const val DOMAIN_LAYER = "..domain.."
        private const val PORT_LAYER = "..port.."
        private const val USE_CASE_LAYER = "..usecase.."
        private const val COMMAND_LAYER = "..command.."
        private const val CONFIG_LAYER = "..config.."
        private const val INPUT_ADAPTER_LAYER = "..adapter.input.."
        private const val OUTPUT_ADAPTER_LAYER = "..adapter.output.."

        private val FRAMEWORK_PACKAGES = arrayOf(
            "io.quarkus..",
            "jakarta..",
            "org.jooq..",
            "com.fasterxml..",
            "io.smallrye..",
            "org.eclipse..",
            "dev.langchain4j..",
        )

        private val INNER_LAYER_PACKAGES = arrayOf(
            "..adapter..",
            "..usecase..",
            "..command..",
            "..port..",
            "..config..",
        )
    }

    @BeforeAll
    fun importClasses() {
        importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.simarel.vkbot")
    }

    @Test
    fun `L1 domain layer depends on no framework and no other layer`() {
        noClasses()
            .that()
            .resideInAPackage(DOMAIN_LAYER)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*(FRAMEWORK_PACKAGES + INNER_LAYER_PACKAGES))
            .check(importedClasses)
    }

    @Test
    fun `L2 port layer depends on no framework and no implementation layer`() {
        noClasses()
            .that()
            .resideInAPackage(PORT_LAYER)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                *(FRAMEWORK_PACKAGES + arrayOf("..adapter..", "..usecase..", "..command..", CONFIG_LAYER)),
            )
            .check(importedClasses)
    }

    @Test
    fun `L3 use cases and commands depend on no adapter, database or transport type`() {
        noClasses()
            .that()
            .resideInAnyPackage(USE_CASE_LAYER, COMMAND_LAYER)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                OUTPUT_ADAPTER_LAYER,
                INPUT_ADAPTER_LAYER,
                "io.quarkus..",
                "org.jooq..",
                "com.fasterxml..",
                "jakarta.ws.rs..",
                "jakarta.json..",
                "dev.langchain4j..",
                "io.smallrye..",
            )
            .check(importedClasses)
    }

    @Test
    fun `L4 input adapters call ports only`() {
        noClasses()
            .that()
            .resideInAPackage(INPUT_ADAPTER_LAYER)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(COMMAND_LAYER, USE_CASE_LAYER, OUTPUT_ADAPTER_LAYER)
            .check(importedClasses)
    }

    @Test
    fun `L5 output adapters do not reach into input adapters or use cases`() {
        noClasses()
            .that()
            .resideInAPackage(OUTPUT_ADAPTER_LAYER)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(INPUT_ADAPTER_LAYER, USE_CASE_LAYER)
            .check(importedClasses)
    }
}
