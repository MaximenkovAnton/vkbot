package com.simarel.vkbot.persistence.command.fetchuserprofile

import com.simarel.vkbot.persistence.testfixtures.FakeFindGroupProfilesByIdsPort
import com.simarel.vkbot.persistence.testfixtures.FakeFindUserProfilesByIdsPort
import com.simarel.vkbot.persistence.testfixtures.FakeGetGroupProfilesBatchPort
import com.simarel.vkbot.persistence.testfixtures.FakeGetUserProfilesBatchPort
import com.simarel.vkbot.persistence.testfixtures.FakeSaveGroupProfilePort
import com.simarel.vkbot.persistence.testfixtures.FakeSaveUserProfilePort
import com.simarel.vkbot.persistence.testfixtures.ProfileActualizationPortProvider
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class FetchUserProfileCommandImplTest {

    @Test
    fun `fresh user profile is not requested from vk`() {
        val fromId = FakeVoProvider.createFromId(USER_ID)
        val fresh = FakeModelProvider.createVkUserProfile(
            id = USER_ID,
            lastUpdated = OffsetDateTime.now().minusDays(1),
        )
        val fetchUsers = FakeGetUserProfilesBatchPort(profiles = listOf(fresh))
        val saveUsers = FakeSaveUserProfilePort()

        command(
            findUsers = FakeFindUserProfilesByIdsPort(profiles = listOf(fresh)),
            saveUsers = saveUsers,
            fetchUsers = fetchUsers,
        ).execute(request(fromId))

        assertTrue(fetchUsers.requestedIds.isEmpty())
        assertTrue(saveUsers.savedProfiles.isEmpty())
    }

    @Test
    fun `only profile older than a month is requested from vk`() {
        val freshFromId = FakeVoProvider.createFromId(101)
        val staleFromId = FakeVoProvider.createFromId(102)
        val fresh = FakeModelProvider.createVkUserProfile(
            id = 101,
            lastUpdated = OffsetDateTime.now().minusDays(1),
        )
        val stale = FakeModelProvider.createVkUserProfile(
            id = 102,
            lastUpdated = OffsetDateTime.now().minusMonths(2),
        )
        val fetched = FakeModelProvider.createVkUserProfile(id = 102, firstName = "New")
        val fetchUsers = FakeGetUserProfilesBatchPort(profiles = listOf(fetched))

        val message = FakeVoProvider.createMessage(
            fromId = freshFromId,
            forwardedMessages = listOf(FakeVoProvider.createMessage(fromId = staleFromId)),
        )
        command(
            findUsers = FakeFindUserProfilesByIdsPort(profiles = listOf(fresh, stale)),
            fetchUsers = fetchUsers,
        ).execute(ProfileActualizationPortProvider.createFetchUserProfileRequest(message = message))

        assertEquals(listOf(staleFromId), fetchUsers.requestedIds.single())
    }

    @Test
    fun `stale user profile replaces provided details and keeps the stored ones`() {
        val fromId = FakeVoProvider.createFromId(USER_ID)
        val stored = FakeModelProvider.createVkUserProfile(
            id = USER_ID,
            firstName = "Old",
            lastName = "Petrov",
            screenName = "old_login",
            birthDate = "01.01.1990",
            city = "Moscow",
            lastUpdated = OffsetDateTime.now().minusMonths(2),
            details = mapOf("about" to "old about", "career" to listOf(mapOf("company" to "Acme"))),
        )
        val fetched = FakeModelProvider.createVkUserProfile(
            id = USER_ID,
            firstName = "New",
            lastName = null,
            screenName = null,
            birthDate = null,
            city = null,
            details = mapOf("career" to emptyList<Any?>()),
        )
        val saveUsers = FakeSaveUserProfilePort()

        val before = OffsetDateTime.now()
        command(
            findUsers = FakeFindUserProfilesByIdsPort(profiles = listOf(stored)),
            saveUsers = saveUsers,
            fetchUsers = FakeGetUserProfilesBatchPort(profiles = listOf(fetched)),
        ).execute(request(fromId))

        val saved = saveUsers.savedProfiles.single()
        assertEquals("New", saved.firstName)
        assertEquals("Petrov", saved.lastName)
        assertEquals("old_login", saved.screenName)
        assertEquals("01.01.1990", saved.birthDate)
        assertEquals("Moscow", saved.city)
        assertEquals("old about", saved.details["about"])
        assertEquals(emptyList<Any?>(), saved.details["career"])
        assertFalse(saved.lastUpdated.isBefore(before))
    }

    @Test
    fun `missing user profile is saved without history`() {
        val fromId = FakeVoProvider.createFromId(USER_ID)
        val fetched = FakeModelProvider.createVkUserProfile(
            id = USER_ID,
            firstName = "New",
            details = mapOf("about" to "about me"),
        )
        val saveUsers = FakeSaveUserProfilePort()

        val before = OffsetDateTime.now()
        command(
            saveUsers = saveUsers,
            fetchUsers = FakeGetUserProfilesBatchPort(profiles = listOf(fetched)),
        ).execute(request(fromId))

        val saved = saveUsers.savedProfiles.single()
        assertEquals("New", saved.firstName)
        assertEquals("about me", saved.details["about"])
        assertFalse(saved.lastUpdated.isBefore(before))
        assertNull(saveUsers.requests.single().changes)
    }

    @Test
    fun `stale group profile updates changed fields and keeps the old ones when vk has no data`() {
        val groupId = FakeVoProvider.createFromId(-GROUP_ID)
        val stored = FakeModelProvider.createVkGroupProfile(
            id = -GROUP_ID,
            name = "Old group",
            screenName = "old_group",
            lastUpdated = OffsetDateTime.now().minusMonths(2),
        )
        val fetched = FakeModelProvider.createVkGroupProfile(
            id = -GROUP_ID,
            name = "New group",
            screenName = null,
        )
        val saveGroups = FakeSaveGroupProfilePort()

        val before = OffsetDateTime.now()
        command(
            findGroups = FakeFindGroupProfilesByIdsPort(profiles = listOf(stored)),
            saveGroups = saveGroups,
            fetchGroups = FakeGetGroupProfilesBatchPort(profiles = listOf(fetched)),
        ).execute(request(groupId))

        val saved = saveGroups.savedProfiles.single()
        assertEquals(-GROUP_ID, saved.id.value)
        assertEquals("New group", saved.name)
        assertEquals("old_group", saved.screenName)
        assertFalse(saved.lastUpdated.isBefore(before))
    }

    @Test
    fun `stale user profile stores only the changed profile fields in history`() {
        val fromId = FakeVoProvider.createFromId(USER_ID)
        val stored = FakeModelProvider.createVkUserProfile(
            id = USER_ID,
            city = "Moscow",
            lastUpdated = OffsetDateTime.now().minusMonths(2),
            details = mapOf("about" to "about me"),
        )
        val fetched = FakeModelProvider.createVkUserProfile(
            id = USER_ID,
            city = "Minsk",
            details = mapOf("about" to "about me"),
        )
        val saveUsers = FakeSaveUserProfilePort()

        command(
            findUsers = FakeFindUserProfilesByIdsPort(profiles = listOf(stored)),
            saveUsers = saveUsers,
            fetchUsers = FakeGetUserProfilesBatchPort(profiles = listOf(fetched)),
        ).execute(request(fromId))

        val changes = saveUsers.requests.single().changes
        assertEquals(setOf("city"), changes?.keys)
        assertEquals(mapOf("old" to "Moscow", "new" to "Minsk"), changes?.getValue("city"))
    }

    @Test
    fun `actualization without changes does not write profile history`() {
        val fromId = FakeVoProvider.createFromId(USER_ID)
        val details = mapOf<String, Any?>("about" to "about me")
        val stored = FakeModelProvider.createVkUserProfile(
            id = USER_ID,
            lastUpdated = OffsetDateTime.now().minusMonths(2),
            details = details,
        )
        val fetched = FakeModelProvider.createVkUserProfile(id = USER_ID, details = details)
        val saveUsers = FakeSaveUserProfilePort()

        command(
            findUsers = FakeFindUserProfilesByIdsPort(profiles = listOf(stored)),
            saveUsers = saveUsers,
            fetchUsers = FakeGetUserProfilesBatchPort(profiles = listOf(fetched)),
        ).execute(request(fromId))

        assertNull(saveUsers.requests.single().changes)
    }

    @Test
    fun `vk failure keeps stored profiles untouched and does not fail the command`() {
        val fromId = FakeVoProvider.createFromId(USER_ID)
        val stored = FakeModelProvider.createVkUserProfile(
            id = USER_ID,
            lastUpdated = OffsetDateTime.now().minusMonths(2),
        )
        val saveUsers = FakeSaveUserProfilePort()

        val response = command(
            findUsers = FakeFindUserProfilesByIdsPort(profiles = listOf(stored)),
            saveUsers = saveUsers,
            fetchUsers = FakeGetUserProfilesBatchPort(failure = RuntimeException("vk is down")),
        ).execute(request(fromId))

        assertTrue(response.success)
        assertTrue(saveUsers.savedProfiles.isEmpty())
    }

    private fun request(fromId: FromId): FetchUserProfileRequest {
        val message = FakeVoProvider.createMessage(fromId = fromId)
        return ProfileActualizationPortProvider.createFetchUserProfileRequest(message = message)
    }

    private fun command(
        findUsers: FakeFindUserProfilesByIdsPort = FakeFindUserProfilesByIdsPort(),
        findGroups: FakeFindGroupProfilesByIdsPort = FakeFindGroupProfilesByIdsPort(),
        saveUsers: FakeSaveUserProfilePort = FakeSaveUserProfilePort(),
        saveGroups: FakeSaveGroupProfilePort = FakeSaveGroupProfilePort(),
        fetchUsers: FakeGetUserProfilesBatchPort = FakeGetUserProfilesBatchPort(),
        fetchGroups: FakeGetGroupProfilesBatchPort = FakeGetGroupProfilesBatchPort(),
    ) = FetchUserProfileCommandImpl(
        findUserProfilesByIdsPort = findUsers,
        findGroupProfilesByIdsPort = findGroups,
        saveGroupProfilePort = saveGroups,
        saveUserProfilePort = saveUsers,
        getUserProfilesBatchPort = fetchUsers,
        getGroupProfilesBatchPort = fetchGroups,
    )

    private companion object {
        const val USER_ID = 100L
        const val GROUP_ID = 200L
    }
}
