package com.simarel.vkbot.greetings.command.collect

import com.simarel.vkbot.greetings.domain.BirthdayGreetingPeopleEmptyValidationException
import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.greetings.test.FakeFindGroupChatPeerIds
import com.simarel.vkbot.greetings.test.FakeFindStoredUserProfilesByIds
import com.simarel.vkbot.greetings.test.FakeFindUserBirthdayFacts
import com.simarel.vkbot.greetings.test.FakeFindUserChatPeerIds
import com.simarel.vkbot.greetings.test.FakeFindUserProfilesByBirthDate
import com.simarel.vkbot.greetings.test.SystemLoggerCapture
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakeGreetingPortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.MonthDay
import java.util.logging.Level
import kotlin.test.assertFailsWith

class CollectGreetingPlansCommandTest {

    @Test
    fun `holidays are planned once per group chat`() {
        val first = FakeVoProvider.createPeerId(2_000_000_001)
        val second = FakeVoProvider.createPeerId(2_000_000_002)

        val response = command(groupPeerIds = setOf(second, first))
            .execute(FakeGreetingProvider.createCollectGreetingPlansRequest(NEW_YEAR))

        assertEquals(listOf(first, second), response.plans.map { it.peerId })
        assertTrue(
            response.plans.all { plan ->
                plan is GreetingPlan.HolidayGreeting && plan.holiday == Holiday.NEW_YEAR
            },
        )
    }

    @Test
    fun `birthdays are planned in every chat where the person wrote, including a personal chat`() {
        val personalChat = FakeVoProvider.createPeerId(1_000_000_001)
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(101)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
        )

        val response = command(
            userChats = mapOf(user to setOf(personalChat, groupChat)),
            profileResponses = listOf(listOf(profile)),
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(PLAIN_DAY))

        assertEquals(listOf(personalChat, groupChat), response.plans.map { it.peerId })
        assertTrue(
            response.plans.all { plan ->
                plan is GreetingPlan.BirthdayGreeting && plan.names() == listOf("Иван Петров")
            },
        )
    }

    @Test
    fun `a holiday and a birthday on the same date are two plans with the birthday last`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(101)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
        )

        val response = command(
            groupPeerIds = setOf(groupChat),
            userChats = mapOf(user to setOf(groupChat)),
            profileResponses = listOf(listOf(profile)),
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(PROGRAMMER_DAY))

        assertEquals(2, response.plans.size)
        val holidayPlan = response.plans[0]
        assertEquals(groupChat, holidayPlan.peerId)
        assertEquals(Holiday.PROGRAMMER_DAY, (holidayPlan as GreetingPlan.HolidayGreeting).holiday)
        val birthdayPlan = response.plans[1]
        assertEquals(groupChat, birthdayPlan.peerId)
        assertEquals(listOf("Иван Петров"), (birthdayPlan as GreetingPlan.BirthdayGreeting).names())
    }

    @Test
    fun `a profile without chats is skipped with a warning`() {
        val user = FakeVoProvider.createFromId(101)
        val profile = FakeModelProvider.createVkUserProfile(id = user.value, screenName = null)

        SystemLoggerCapture(CollectGreetingPlansCommandImpl::class.java.name).use { capture ->
            val response = command(profileResponses = listOf(listOf(profile)))
                .execute(FakeGreetingProvider.createCollectGreetingPlansRequest(PLAIN_DAY))

            assertTrue(response.plans.isEmpty())
            assertTrue(capture.messages(Level.WARNING).any { it.contains("user=${user.value}") })
        }
    }

    @Test
    fun `first of march of a non-leap year asks for both days and merges the birthdays`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val marchUser = FakeVoProvider.createFromId(201)
        val februaryUser = FakeVoProvider.createFromId(202)
        val marchProfile = FakeModelProvider.createVkUserProfile(
            id = marchUser.value,
            firstName = "Анна",
            lastName = "Смирнова",
            screenName = null,
        )
        val februaryProfile = FakeModelProvider.createVkUserProfile(
            id = februaryUser.value,
            firstName = "Ёлкин",
            lastName = "Иван",
            screenName = null,
        )
        val profiles = FakeFindUserProfilesByBirthDate(
            mutableListOf(
                FakeGreetingPortProvider.createFindUserProfilesByBirthDateResponse(profiles = listOf(marchProfile)),
                FakeGreetingPortProvider.createFindUserProfilesByBirthDateResponse(profiles = listOf(februaryProfile)),
            ),
        )

        val response = CollectGreetingPlansCommandImpl(
            FakeFindGroupChatPeerIds(),
            FakeFindUserChatPeerIds(
                mapOf(
                    marchUser to setOf(groupChat),
                    februaryUser to setOf(groupChat),
                ),
            ),
            profiles,
            FakeFindStoredUserProfilesByIds(),
            FakeFindUserBirthdayFacts(),
            true,
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(LocalDate.parse("2025-03-01")))

        assertEquals(listOf(1 to 3, 29 to 2), profiles.executeCalls.map { it.day to it.month })
        assertEquals(
            listOf("Ёлкин Иван", "Анна Смирнова"),
            (response.plans.single() as GreetingPlan.BirthdayGreeting).names(),
        )
    }

    @Test
    fun `an empty event set produces no plans`() {
        val response = command().execute(FakeGreetingProvider.createCollectGreetingPlansRequest(PLAIN_DAY))

        assertTrue(response.plans.isEmpty())
    }

    @Test
    fun `birthdays are sorted by string comparison, not by a locale collator`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val firstUser = FakeVoProvider.createFromId(201)
        val secondUser = FakeVoProvider.createFromId(202)
        val firstProfile = FakeModelProvider.createVkUserProfile(
            id = firstUser.value,
            firstName = "Ёлкин",
            lastName = "Иван",
            screenName = null,
        )
        val secondProfile = FakeModelProvider.createVkUserProfile(
            id = secondUser.value,
            firstName = "Анна",
            lastName = "Смирнова",
            screenName = null,
        )

        val response = command(
            userChats = mapOf(firstUser to setOf(groupChat), secondUser to setOf(groupChat)),
            profileResponses = listOf(listOf(secondProfile, firstProfile)),
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(PLAIN_DAY))

        assertEquals(
            listOf("Ёлкин Иван", "Анна Смирнова"),
            (response.plans.single() as GreetingPlan.BirthdayGreeting).names(),
        )
    }

    @Test
    fun `identical formatted names are not duplicated`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val firstUser = FakeVoProvider.createFromId(301)
        val secondUser = FakeVoProvider.createFromId(302)
        val firstProfile = FakeModelProvider.createVkUserProfile(
            id = firstUser.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
        )
        val secondProfile = FakeModelProvider.createVkUserProfile(
            id = secondUser.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
        )

        val response = command(
            userChats = mapOf(firstUser to setOf(groupChat), secondUser to setOf(groupChat)),
            profileResponses = listOf(listOf(firstProfile, secondProfile)),
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(PLAIN_DAY))

        assertEquals(listOf("Иван Петров"), (response.plans.single() as GreetingPlan.BirthdayGreeting).names())
    }

    @Test
    fun `a birthday plan without people is rejected`() {
        val peerId = FakeVoProvider.createPeerId(2_000_000_001)

        assertFailsWith<BirthdayGreetingPeopleEmptyValidationException> {
            GreetingPlan.BirthdayGreeting(peerId = peerId, people = emptyList())
        }
    }

    @Test
    fun `hidden birthday confirmed fact is congratulated in every chat without vk date`() {
        val firstChat = FakeVoProvider.createPeerId(1_000_000_001)
        val secondChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(7_400_001)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
            birthDate = null,
        )
        val birthdayFacts = FakeFindUserBirthdayFacts(mapOf(MAY_14 to listOf(birthdayFact(userId = user.value))))
        val storedProfiles = FakeFindStoredUserProfilesByIds(profiles = listOf(profile))

        fun run(date: LocalDate) = command(
            userChats = mapOf(user to setOf(firstChat, secondChat)),
            storedProfiles = storedProfiles,
            birthdayFacts = birthdayFacts,
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(date))

        val currentYear = run(FACT_DAY)

        assertEquals(listOf(firstChat, secondChat), currentYear.plans.map { it.peerId })
        assertTrue(
            currentYear.plans.all { plan -> (plan as GreetingPlan.BirthdayGreeting).names() == listOf("Иван Петров") },
        )

        val nextYear = run(FACT_DAY_NEXT_YEAR)

        assertEquals(listOf(firstChat, secondChat), nextYear.plans.map { it.peerId })
        assertTrue(
            nextYear.plans.all { plan -> (plan as GreetingPlan.BirthdayGreeting).names() == listOf("Иван Петров") },
        )
    }

    @Test
    fun `hidden birthday fact of vk birthday person is not duplicated`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(101)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
        )
        val storedProfiles = FakeFindStoredUserProfilesByIds(profiles = listOf(profile))
        val birthdayFacts = FakeFindUserBirthdayFacts(mapOf(MAY_14 to listOf(birthdayFact(userId = user.value))))

        val response = command(
            userChats = mapOf(user to setOf(groupChat)),
            profileResponses = listOf(listOf(profile)),
            storedProfiles = storedProfiles,
            birthdayFacts = birthdayFacts,
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(FACT_DAY))

        assertEquals(
            listOf("Иван Петров"),
            (response.plans.single() as GreetingPlan.BirthdayGreeting).names(),
        )
        assertTrue(storedProfiles.executeCalls.isEmpty())
    }

    @Test
    fun `hidden birthday fact is ignored when vk date is known on another date`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(101)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
            birthDate = "15.05.1990",
        )
        val birthdayFacts = FakeFindUserBirthdayFacts(mapOf(MAY_14 to listOf(birthdayFact(userId = user.value))))
        val storedProfiles = FakeFindStoredUserProfilesByIds(profiles = listOf(profile))

        val onFactDate = command(
            userChats = mapOf(user to setOf(groupChat)),
            storedProfiles = storedProfiles,
            birthdayFacts = birthdayFacts,
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(FACT_DAY))

        assertTrue(onFactDate.plans.isEmpty())

        val onVkDate = command(
            userChats = mapOf(user to setOf(groupChat)),
            profileResponses = listOf(listOf(profile)),
            storedProfiles = FakeFindStoredUserProfilesByIds(profiles = listOf(profile)),
            birthdayFacts = FakeFindUserBirthdayFacts(mapOf(MAY_14 to listOf(birthdayFact(userId = user.value)))),
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(VK_DATE_DAY))

        assertEquals(
            listOf("Иван Петров"),
            (onVkDate.plans.single() as GreetingPlan.BirthdayGreeting).names(),
        )
    }

    @Test
    fun `candidate birthday fact is not congratulated`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(7_400_003)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
            birthDate = null,
        )
        val storedProfiles = FakeFindStoredUserProfilesByIds(profiles = listOf(profile))
        val birthdayFacts = FakeFindUserBirthdayFacts(
            mapOf(MAY_14 to listOf(birthdayFact(userId = user.value, status = UserFactStatus.CANDIDATE))),
        )

        val response = command(
            userChats = mapOf(user to setOf(groupChat)),
            storedProfiles = storedProfiles,
            birthdayFacts = birthdayFacts,
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(FACT_DAY))

        assertTrue(response.plans.isEmpty())
        assertTrue(storedProfiles.executeCalls.isEmpty())
    }

    @Test
    fun `hidden birthdays kill switch keeps vk birthdays and holidays`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(101)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Анна",
            lastName = "Иванова",
            screenName = null,
            birthDate = "01.01.1990",
        )
        val birthdayFacts = FakeFindUserBirthdayFacts(mapOf(MAY_14 to listOf(birthdayFact(userId = user.value))))

        val response = command(
            groupPeerIds = setOf(groupChat),
            userChats = mapOf(user to setOf(groupChat)),
            profileResponses = listOf(listOf(profile)),
            birthdayFacts = birthdayFacts,
            hiddenBirthdaysEnabled = false,
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(NEW_YEAR))

        assertTrue(birthdayFacts.executeCalls.isEmpty())
        assertEquals(2, response.plans.size)
        assertEquals(Holiday.NEW_YEAR, (response.plans[0] as GreetingPlan.HolidayGreeting).holiday)
        assertEquals(listOf("Анна Иванова"), (response.plans[1] as GreetingPlan.BirthdayGreeting).names())
    }

    @Test
    fun `hidden birthday on february 29 follows leap year rule`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(7_400_006)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
            birthDate = null,
        )

        fun run(date: LocalDate, birthdayFacts: FakeFindUserBirthdayFacts) = command(
            userChats = mapOf(user to setOf(groupChat)),
            storedProfiles = FakeFindStoredUserProfilesByIds(profiles = listOf(profile)),
            birthdayFacts = birthdayFacts,
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(date))

        fun fact() = FakeFindUserBirthdayFacts(
            mapOf(FEB_29 to listOf(birthdayFact(userId = user.value, monthDay = FEB_29))),
        )

        val leapDay = run(LEAP_DAY, fact())

        assertEquals(
            listOf("Иван Петров"),
            (leapDay.plans.single() as GreetingPlan.BirthdayGreeting).names(),
        )

        val marchFirstFacts = fact()
        val marchFirst = run(MARCH_FIRST, marchFirstFacts)

        assertEquals(
            listOf("Иван Петров"),
            (marchFirst.plans.single() as GreetingPlan.BirthdayGreeting).names(),
        )
        assertEquals(listOf(MonthDay.of(3, 1), FEB_29), marchFirstFacts.executeCalls.map { it.monthDay })

        assertTrue(run(FEB_28, fact()).plans.isEmpty())
    }

    @Test
    fun `hidden birthday without stored profile is skipped with warning`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(7_400_005)
        val birthdayFacts = FakeFindUserBirthdayFacts(mapOf(MAY_14 to listOf(birthdayFact(userId = user.value))))

        SystemLoggerCapture(CollectGreetingPlansCommandImpl::class.java.name).use { capture ->
            val response = command(
                userChats = mapOf(user to setOf(groupChat)),
                storedProfiles = FakeFindStoredUserProfilesByIds(),
                birthdayFacts = birthdayFacts,
            ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(FACT_DAY))

            assertTrue(response.plans.isEmpty())
            val warnings = capture.messages(Level.WARNING)
            assertTrue(warnings.any { message -> message.contains("user=${user.value}") })
            assertTrue(warnings.none { message -> message.contains("Иван Петров") || message.contains("14.05") })
        }
    }

    @Test
    fun `hidden birthday read failure falls back to vk plans`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(101)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
        )

        SystemLoggerCapture(CollectGreetingPlansCommandImpl::class.java.name).use { capture ->
            val response = command(
                userChats = mapOf(user to setOf(groupChat)),
                profileResponses = listOf(listOf(profile)),
                storedProfiles = FakeFindStoredUserProfilesByIds(profiles = listOf(profile)),
                birthdayFacts = FakeFindUserBirthdayFacts(failure = IllegalStateException("boom")),
            ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(FACT_DAY))

            assertEquals(
                listOf("Иван Петров"),
                (response.plans.single() as GreetingPlan.BirthdayGreeting).names(),
            )
            val warnings = capture.messages(Level.WARNING)
            assertTrue(
                warnings.any { message ->
                    message.contains("Hidden birthday facts read failed") && message.contains("IllegalStateException")
                },
            )
            assertTrue(warnings.none { message -> message.contains("Иван Петров") })
        }
    }

    @Test
    fun `hidden birthday profiles read failure is logged and skipped`() {
        val groupChat = FakeVoProvider.createPeerId(2_000_000_001)
        val user = FakeVoProvider.createFromId(7_400_007)
        val profile = FakeModelProvider.createVkUserProfile(
            id = user.value,
            firstName = "Иван",
            lastName = "Петров",
            screenName = null,
            birthDate = null,
        )
        val birthdayFacts = FakeFindUserBirthdayFacts(mapOf(MAY_14 to listOf(birthdayFact(userId = user.value))))

        SystemLoggerCapture(CollectGreetingPlansCommandImpl::class.java.name).use { capture ->
            val response = command(
                userChats = mapOf(user to setOf(groupChat)),
                storedProfiles = FakeFindStoredUserProfilesByIds(
                    profiles = listOf(profile),
                    failure = IllegalStateException("boom"),
                ),
                birthdayFacts = birthdayFacts,
            ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(FACT_DAY))

            assertTrue(response.plans.isEmpty())
            val warnings = capture.messages(Level.WARNING)
            assertTrue(
                warnings.any { message ->
                    message.contains("Hidden birthday facts read failed") && message.contains("IllegalStateException")
                },
            )
            assertTrue(warnings.none { message -> message.contains("Иван Петров") })
        }
    }

    private fun GreetingPlan.BirthdayGreeting.names(): List<String> = people.map { it.name }

    private fun command(
        groupPeerIds: Set<PeerId> = emptySet(),
        userChats: Map<FromId, Set<PeerId>> = emptyMap(),
        profileResponses: List<List<VkUserProfile>> = emptyList(),
        storedProfiles: FakeFindStoredUserProfilesByIds = FakeFindStoredUserProfilesByIds(),
        birthdayFacts: FakeFindUserBirthdayFacts = FakeFindUserBirthdayFacts(),
        hiddenBirthdaysEnabled: Boolean = true,
    ): CollectGreetingPlansCommandImpl = CollectGreetingPlansCommandImpl(
        FakeFindGroupChatPeerIds(peerIds = groupPeerIds),
        FakeFindUserChatPeerIds(chats = userChats),
        FakeFindUserProfilesByBirthDate(
            responses = profileResponses.mapTo(mutableListOf()) {
                FakeGreetingPortProvider.createFindUserProfilesByBirthDateResponse(profiles = it)
            },
        ),
        storedProfiles,
        birthdayFacts,
        hiddenBirthdaysEnabled,
    )

    private fun birthdayFact(
        userId: Long,
        status: UserFactStatus = UserFactStatus.CONFIRMED,
        monthDay: MonthDay = MAY_14,
    ): UserFact = FakeModelProvider.createUserFact(
        userId = userId,
        category = UserFactCategory.PROFILE_ATTRIBUTE,
        factKey = "birthday",
        value = "ДР: %02d.%02d".format(monthDay.dayOfMonth, monthDay.monthValue),
        valuePayload = mapOf("monthDay" to "%02d-%02d".format(monthDay.monthValue, monthDay.dayOfMonth)),
        status = status,
    )

    private companion object {
        val NEW_YEAR: LocalDate = LocalDate.parse("2026-01-01")
        val PROGRAMMER_DAY: LocalDate = LocalDate.parse("2026-09-13")
        val PLAIN_DAY: LocalDate = LocalDate.parse("2026-09-14")
        val VK_DATE_DAY: LocalDate = LocalDate.parse("2026-05-15")
        val FACT_DAY: LocalDate = LocalDate.parse("2026-05-14")
        val FACT_DAY_NEXT_YEAR: LocalDate = LocalDate.parse("2027-05-14")
        val LEAP_DAY: LocalDate = LocalDate.parse("2024-02-29")
        val MARCH_FIRST: LocalDate = LocalDate.parse("2025-03-01")
        val FEB_28: LocalDate = LocalDate.parse("2025-02-28")
        val MAY_14: MonthDay = MonthDay.of(5, 14)
        val FEB_29: MonthDay = MonthDay.of(2, 29)
    }
}
