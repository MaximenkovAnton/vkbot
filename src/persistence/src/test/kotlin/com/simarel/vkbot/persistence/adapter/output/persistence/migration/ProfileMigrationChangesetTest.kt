package com.simarel.vkbot.persistence.adapter.output.persistence.migration

import liquibase.change.core.AddColumnChange
import liquibase.change.core.DeleteDataChange
import liquibase.change.core.DropColumnChange
import liquibase.change.core.RawSQLChange
import liquibase.changelog.ChangeLogParameters
import liquibase.parser.ChangeLogParserFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileMigrationChangesetTest {

    @Test
    fun `changeset 008 declares an unbounded history delete before dropping profile data`() {
        val changeSets = changeSets()

        assertEquals(
            listOf("008-add-profile-details", "008-clear-profile-history", "008-drop-profile-data"),
            changeSets.map { it.id },
        )
        val add = changeSets[0].changes.single() as AddColumnChange
        assertEquals("vk_user_profiles", add.tableName)
        assertEquals("profile_details", add.columns.single().name)
        assertEquals("JSONB", add.columns.single().type)
        val delete = changeSets[1].changes[1] as DeleteDataChange
        assertEquals("vk_user_profile_history", delete.tableName)
        assertNull(delete.where)
        assertEquals("profile_data", (changeSets[2].changes[1] as DropColumnChange).columnName)
        val master = masterText()
        assertTrue(
            master.indexOf("007-add-user-profile-data-and-history.yaml") <
                master.indexOf("008-replace-profile-data-with-human-details.yaml"),
        )
    }

    @Test
    fun `changeset 008 bounds the lock wait with lock timeout`() {
        val changeSets = changeSets()

        assertEquals("SET LOCAL lock_timeout = '10s'", (changeSets[1].changes[0] as RawSQLChange).sql)
        assertEquals("SET LOCAL lock_timeout = '10s'", (changeSets[2].changes[0] as RawSQLChange).sql)
    }

    private fun changeSets() = ChangeLogParserFactory.getInstance()
        .getParser(CHANGELOG, ClassLoaderResourceAccessor())
        .parse(CHANGELOG, ChangeLogParameters(), ClassLoaderResourceAccessor())
        .changeSets

    private fun masterText(): String = ClassLoaderResourceAccessor().get(MASTER)
        .openInputStream()
        .bufferedReader()
        .use { it.readText() }

    private companion object {
        const val CHANGELOG = "db/changelog/changes/008-replace-profile-data-with-human-details.yaml"
        const val MASTER = "db/changelog/db.changelog-master.yaml"
    }
}
