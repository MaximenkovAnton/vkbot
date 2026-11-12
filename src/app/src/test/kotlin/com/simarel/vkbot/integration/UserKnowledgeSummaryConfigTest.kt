package com.simarel.vkbot.integration

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.eclipse.microprofile.config.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

private const val FACT_THRESHOLD = 20

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class UserKnowledgeSummaryConfigTest {

    @Inject
    lateinit var config: Config

    @Test
    fun `readsFactThreshold`() {
        assertEquals(
            FACT_THRESHOLD,
            config.getValue("knowledge.summary-fact-threshold", Int::class.java),
        )
    }

    @Test
    fun `readsIntervalAsDuration`() {
        assertEquals(
            Duration.ofDays(14),
            config.getValue("knowledge.summary-interval", Duration::class.java),
        )
    }

    @Test
    fun `declaresSummarySystemPrompt`() {
        val prompt = config.getValue("aiKnowledgeSummarySystemprompt", String::class.java)

        assertFalse(prompt.isBlank())
        assertTrue(prompt.contains("возможно"), prompt)
    }
}
