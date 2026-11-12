package com.simarel.vkbot.integration

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.eclipse.microprofile.config.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class NewsDigestConfigTest {

    @Inject
    lateinit var config: Config

    @Test
    fun `readsNewsPersonalizationFlagAsTrue`() {
        assertEquals(
            true,
            config.getValue("knowledge.news-personalization.enabled", Boolean::class.java),
        )
    }
}
