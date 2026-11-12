package com.simarel.vkbot.integration

import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.eclipse.microprofile.config.ConfigProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import javax.sql.DataSource

private const val USER_ID = 7_300_060L
private val SUMMARY_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-01T00:00:00Z")

class KnowledgeDisabledTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "vk.longpoll.enabled" to "false",
        "knowledge.enabled" to "false",
    )
}

@QuarkusTest
@TestProfile(KnowledgeDisabledTestProfile::class)
class UserKnowledgeReadDisabledIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var findUserKnowledgePort: FindUserKnowledgePort

    private lateinit var fixtures: UserKnowledgeReadFixtures

    @BeforeEach
    fun setUp() {
        fixtures = UserKnowledgeReadFixtures(dataSource)
        fixtures.deleteKnowledge(USER_ID, USER_ID)
    }

    @AfterEach
    fun tearDown() {
        fixtures.deleteKnowledge(USER_ID, USER_ID)
    }

    @Test
    fun `readsKnowledgeWhenExtractionDisabled`() {
        assertFalse(ConfigProvider.getConfig().getValue("knowledge.enabled", Boolean::class.java))
        fixtures.insertFact(userId = USER_ID, value = "футбол", lastConfirmedAt = SUMMARY_AT)

        val knowledge = findUserKnowledgePort.execute(
            FakePersistencePortProvider.createFindUserKnowledgeRequest(
                userIds = listOf(FakeVoProvider.createFromId(USER_ID)),
            ),
        ).knowledge.getValue(FakeVoProvider.createFromId(USER_ID))

        assertEquals(1, knowledge.facts.size)
        assertEquals("футбол", knowledge.facts.single().value)
    }
}
