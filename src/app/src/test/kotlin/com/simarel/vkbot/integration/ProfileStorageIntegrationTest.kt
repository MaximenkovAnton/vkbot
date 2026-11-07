package com.simarel.vkbot.integration

import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakeProfilePortProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import javax.sql.DataSource

class ProfileStorageTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf("vk.longpoll.enabled" to "false")
}

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class ProfileStorageIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var findStoredUserProfilesByIdsPort: FindStoredUserProfilesByIdsPort

    @Inject
    lateinit var findStoredGroupProfilesByIdsPort: FindStoredGroupProfilesByIdsPort

    @Test
    fun `stored user profile is read together with its last actualization date`() {
        val lastUpdated = OffsetDateTime.now().minusDays(3)
        upsertUserProfile(id = USER_ID, firstName = "Stored", lastUpdated = lastUpdated)

        val response = findStoredUserProfilesByIdsPort.execute(
            FakeProfilePortProvider.createFindStoredUserProfilesByIdsRequest(
                ids = listOf(FakeVoProvider.createFromId(USER_ID)),
            ),
        )

        val profile = response.profiles.single { it.id.value == USER_ID }
        assertEquals("Stored", profile.firstName)
        assertNotNull(profile.lastUpdated)
        assertEquals(lastUpdated.toInstant().toEpochMilli(), profile.lastUpdated.toInstant().toEpochMilli())
    }

    @Test
    fun `stored group profile is read by the same negative id it was saved with`() {
        upsertGroupProfile(id = GROUP_ID, name = "Stored group")

        val response = findStoredGroupProfilesByIdsPort.execute(
            FakeProfilePortProvider.createFindStoredGroupProfilesByIdsRequest(
                ids = listOf(FakeVoProvider.createFromId(GROUP_ID)),
            ),
        )

        val profile = response.profiles.single { it.id.value == GROUP_ID }
        assertEquals("Stored group", profile.name)
    }

    private fun upsertUserProfile(id: Long, firstName: String, lastUpdated: OffsetDateTime) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(UPSERT_USER_PROFILE_SQL).use { statement ->
                statement.setLong(1, id)
                statement.setString(2, firstName)
                statement.setObject(3, lastUpdated)
                statement.executeUpdate()
            }
        }
    }

    private fun upsertGroupProfile(id: Long, name: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(UPSERT_GROUP_PROFILE_SQL).use { statement ->
                statement.setLong(1, id)
                statement.setString(2, name)
                statement.executeUpdate()
            }
        }
    }

    private companion object {
        const val USER_ID = 7_500_001L
        const val GROUP_ID = -7_500_002L
        const val UPSERT_USER_PROFILE_SQL =
            "INSERT INTO vk_user_profiles " +
                "(id, first_name, last_name, screen_name, birth_date, city, last_updated, created_at) " +
                "VALUES (?, ?, NULL, NULL, NULL, NULL, ?, now()) " +
                "ON CONFLICT (id) DO UPDATE SET first_name = EXCLUDED.first_name, " +
                "last_updated = EXCLUDED.last_updated"
        const val UPSERT_GROUP_PROFILE_SQL =
            "INSERT INTO vk_group_profiles (id, name, screen_name, last_updated, created_at) " +
                "VALUES (?, ?, NULL, now(), now()) " +
                "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name"
    }
}
