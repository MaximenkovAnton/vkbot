package com.simarel.vkbot.greetings.command.collect

import com.simarel.vkbot.greetings.domain.BirthdayGreetingPeopleEmptyValidationException
import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.greetings.test.FakeFindGroupChatPeerIds
import com.simarel.vkbot.greetings.test.FakeFindUserChatPeerIds
import com.simarel.vkbot.greetings.test.FakeFindUserProfilesByBirthDate
import com.simarel.vkbot.greetings.test.SystemLoggerCapture
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakeGreetingPortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
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
                plan is GreetingPlan.BirthdayGreeting && plan.people == listOf("Иван Петров")
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
        assertEquals(listOf("Иван Петров"), (birthdayPlan as GreetingPlan.BirthdayGreeting).people)
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
        ).execute(FakeGreetingProvider.createCollectGreetingPlansRequest(LocalDate.parse("2025-03-01")))

        assertEquals(listOf(1 to 3, 29 to 2), profiles.executeCalls.map { it.day to it.month })
        assertEquals(
            listOf("Ёлкин Иван", "Анна Смирнова"),
            (response.plans.single() as GreetingPlan.BirthdayGreeting).people,
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
            (response.plans.single() as GreetingPlan.BirthdayGreeting).people,
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

        assertEquals(listOf("Иван Петров"), (response.plans.single() as GreetingPlan.BirthdayGreeting).people)
    }

    @Test
    fun `a birthday plan without people is rejected`() {
        val peerId = FakeVoProvider.createPeerId(2_000_000_001)

        assertFailsWith<BirthdayGreetingPeopleEmptyValidationException> {
            GreetingPlan.BirthdayGreeting(peerId = peerId, people = emptyList())
        }
    }

    private fun command(
        groupPeerIds: Set<PeerId> = emptySet(),
        userChats: Map<FromId, Set<PeerId>> = emptyMap(),
        profileResponses: List<List<VkUserProfile>> = emptyList(),
    ): CollectGreetingPlansCommandImpl = CollectGreetingPlansCommandImpl(
        FakeFindGroupChatPeerIds(peerIds = groupPeerIds),
        FakeFindUserChatPeerIds(chats = userChats),
        FakeFindUserProfilesByBirthDate(
            responses = profileResponses.mapTo(mutableListOf()) {
                FakeGreetingPortProvider.createFindUserProfilesByBirthDateResponse(profiles = it)
            },
        ),
    )

    private companion object {
        val NEW_YEAR: LocalDate = LocalDate.parse("2026-01-01")
        val PROGRAMMER_DAY: LocalDate = LocalDate.parse("2026-09-13")
        val PLAIN_DAY: LocalDate = LocalDate.parse("2026-09-14")
    }
}
