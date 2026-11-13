package com.simarel.vkbot.integration

import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.port.output.persistence.FindUserBirthdayFactsPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.port.FakeGreetingPortProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.eclipse.microprofile.config.Config
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.MonthDay
import java.time.OffsetDateTime
import javax.sql.DataSource

private const val USER_WITH_YEAR = 7_400_001L
private const val USER_WITHOUT_YEAR = 7_400_002L
private const val CANDIDATE_USER = 7_400_003L
private const val HISTORICAL_USER = 7_400_004L
private const val SAVED_FACTS = 5
private const val SKIPPED_FACTS = 0
private const val CONFIRMED_CONFIDENCE = 0.8
private const val CANDIDATE_CONFIDENCE = 0.4
private const val CONFIDENCE_DELTA = 0.0000001
private const val BIRTH_YEAR = 1993
private val OBSERVED_AT: OffsetDateTime = OffsetDateTime.parse("2026-06-01T00:00:00Z")
private val EARLIER_AT: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class HiddenBirthdayKnowledgeIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var findUserBirthdayFactsPort: FindUserBirthdayFactsPort

    @Inject
    lateinit var saveUserFactsPort: SaveUserFactsPort

    @Inject
    lateinit var config: Config

    @BeforeEach
    fun setUp() {
        clearKnowledge()
    }

    @AfterEach
    fun tearDown() {
        clearKnowledge()
    }

    @Test
    fun storesNormalizedBirthdayFactsAndReadsCurrentByMonthDay() {
        val saved = listOf(
            save(
                candidate(USER_WITH_YEAR, "14.05.1993", KnowledgeSourceKind.SELF_STATEMENT, CONFIRMED_CONFIDENCE),
                candidate(USER_WITHOUT_YEAR, "14 мая", KnowledgeSourceKind.SELF_STATEMENT, CONFIRMED_CONFIDENCE),
                candidate(CANDIDATE_USER, "14.05", KnowledgeSourceKind.INFERENCE, 0.9),
            ),
            save(candidate(HISTORICAL_USER, "15.05.1990", KnowledgeSourceKind.SELF_STATEMENT, CONFIRMED_CONFIDENCE)),
            save(
                candidate(
                    HISTORICAL_USER,
                    "14.05",
                    KnowledgeSourceKind.SELF_STATEMENT,
                    CONFIRMED_CONFIDENCE,
                    observedAt = EARLIER_AT,
                ),
            ),
        )

        assertEquals(SAVED_FACTS, saved.sumOf { it.saved })
        assertEquals(SKIPPED_FACTS, saved.sumOf { it.skipped })

        val facts = factsFor(MonthDay.of(5, 14))

        assertEquals(listOf(USER_WITH_YEAR, USER_WITHOUT_YEAR, CANDIDATE_USER), facts.map(UserFact::userId))
        assertNormalizedBirthday(facts[0], expectedYear = BIRTH_YEAR)
        assertNormalizedBirthday(facts[1], expectedYear = null)
        assertFalse(facts[1].valuePayload.containsKey("year"))
        assertEquals(UserFactStatus.CONFIRMED, facts[1].status)
        assertEquals(CONFIRMED_CONFIDENCE, facts[1].confidence, CONFIDENCE_DELTA)
        assertEquals(UserFactStatus.CANDIDATE, facts[2].status)
        assertEquals(CANDIDATE_CONFIDENCE, facts[2].confidence, CONFIDENCE_DELTA)
        assertFalse(facts.any { fact -> fact.userId == HISTORICAL_USER })

        val otherDay = factsFor(MonthDay.of(5, 15))

        assertEquals(listOf(HISTORICAL_USER), otherDay.map(UserFact::userId))
        assertEquals(UserFactStatus.CONFIRMED, otherDay.single().status)
    }

    @Test
    fun defaultExtractionPromptNamesBirthdayKey() {
        val prompt = config.getOptionalValue("aiKnowledgeSystemprompt", String::class.java).orElse("")

        assertTrue(prompt.contains("ключ birthday"))
        assertTrue(prompt.contains("PROFILE_ATTRIBUTE"))
    }

    private fun assertNormalizedBirthday(fact: UserFact, expectedYear: Int?) {
        assertEquals("ДР: 14.05", fact.value)
        assertEquals("05-14", fact.valuePayload["monthDay"])
        assertEquals(expectedYear, (fact.valuePayload["year"] as Number?)?.toInt())
        assertEquals(UserFactStatus.CONFIRMED, fact.status)
        assertEquals(CONFIRMED_CONFIDENCE, fact.confidence, CONFIDENCE_DELTA)
    }

    private fun factsFor(monthDay: MonthDay): List<UserFact> = findUserBirthdayFactsPort
        .execute(FakeGreetingPortProvider.createFindUserBirthdayFactsRequest(monthDay = monthDay))
        .facts

    private fun save(vararg candidates: UserFactCandidate): SaveUserFactsPort.Response = saveUserFactsPort
        .execute(FakePersistencePortProvider.createSaveUserFactsRequest(candidates = candidates.toList()))

    private fun candidate(
        userId: Long,
        value: String,
        sourceKind: KnowledgeSourceKind,
        confidence: Double,
        observedAt: OffsetDateTime = OBSERVED_AT,
    ) = FakeModelProvider.createUserFactCandidate(
        userId = userId,
        peerId = userId,
        category = UserFactCategory.PROFILE_ATTRIBUTE,
        factKey = "birthday",
        value = value,
        sourceKind = sourceKind,
        confidence = confidence,
        observedAt = observedAt,
    )

    private fun clearKnowledge() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "DELETE FROM user_facts WHERE user_id IN " +
                        "($USER_WITH_YEAR, $USER_WITHOUT_YEAR, $CANDIDATE_USER, $HISTORICAL_USER)",
                )
            }
        }
    }
}
