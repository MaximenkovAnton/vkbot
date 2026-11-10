package com.simarel.vkbot.integration

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.sql.DataSource

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class ProfileMigrationIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `changesets applied profile details column instead of profile data`() {
        val columns = profileColumns()

        assertEquals("jsonb", columns["profile_details"])
        assertNull(columns["profile_data"])
    }

    private fun profileColumns(): Map<String, String> = dataSource.connection.use { connection ->
        connection.prepareStatement(COLUMNS_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(rows.getString("column_name"), rows.getString("data_type"))
                    }
                }
            }
        }
    }

    private companion object {
        const val COLUMNS_SQL = "SELECT column_name, data_type FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND table_name = 'vk_user_profiles'"
    }
}
