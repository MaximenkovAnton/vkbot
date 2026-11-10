package com.simarel.vkbot.persistence.adapter.output.persistence.migration

import liquibase.change.Change
import liquibase.change.ColumnConfig
import liquibase.change.core.CreateTableChange
import liquibase.change.core.DeleteDataChange
import liquibase.change.core.InsertDataChange
import liquibase.change.core.LoadDataChange
import liquibase.change.core.RawSQLChange
import liquibase.change.core.UpdateDataChange
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.parser.ChangeLogParserFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

class StatisticsMigrationChangesetTest {

    @Test
    fun `changeset 009 creates the three statistics tables without data changes`() {
        val changes = changeSet().changes

        assertEquals(
            listOf(
                CreateTableChange::class.java,
                CreateTableChange::class.java,
                CreateTableChange::class.java,
                RawSQLChange::class.java,
            ),
            changes.map { it.javaClass },
        )
        assertEquals(
            listOf("user_statistics", "user_chat_statistics", "user_hour_statistics"),
            changes.filterIsInstance<CreateTableChange>().map { it.tableName },
        )
        assertNoDataChanges(changes)
    }

    @Test
    fun `changeset 009 declares not null columns and defaults`() {
        assertUserStatisticsColumns()
        assertUserChatStatisticsColumns()
        assertUserHourStatisticsColumns()
    }

    @Test
    fun `changeset 009 adds the utc hour check constraint and follows 008 in the master changelog`() {
        val rawSql = changeSet().changes.filterIsInstance<RawSQLChange>().single()

        assertEquals(
            "ALTER TABLE user_hour_statistics ADD CONSTRAINT chk_user_hour_statistics_utc_hour " +
                "CHECK (utc_hour BETWEEN 0 AND 23)",
            rawSql.sql,
        )
        val master = masterText()
        assertTrue(
            master.indexOf(EIGHT_CHANGELOG) < master.indexOf(NINE_CHANGELOG),
            "the master changelog must include 009 after 008",
        )
    }

    @Test
    fun `changeset 008 bytes are unchanged`() {
        val file = eightChangesetFile()

        assertTrue(file.isFile, "missing changeset file: $file")
        assertEquals(EIGHT_CHANGESET_SHA256, sha256(file))
    }

    private fun assertUserStatisticsColumns() {
        assertColumn("user_statistics", "user_id", "BIGINT", nullable = false, primaryKey = true)
        assertColumn("user_statistics", "message_count", "BIGINT", nullable = false, defaultValue = "0")
        assertColumn("user_statistics", "total_length", "BIGINT", nullable = false, defaultValue = "0")
        assertColumn("user_statistics", "first_message_at", "TIMESTAMP WITH TIME ZONE", nullable = true)
        assertColumn("user_statistics", "last_message_at", "TIMESTAMP WITH TIME ZONE", nullable = true)
        assertColumn(
            "user_statistics",
            "updated_at",
            "TIMESTAMP WITH TIME ZONE",
            nullable = false,
            defaultValueComputed = "now()",
        )
    }

    private fun assertUserChatStatisticsColumns() {
        assertColumn("user_chat_statistics", "user_id", "BIGINT", nullable = false, primaryKey = true)
        assertColumn("user_chat_statistics", "peer_id", "BIGINT", nullable = false, primaryKey = true)
        assertColumn("user_chat_statistics", "message_count", "BIGINT", nullable = false, defaultValue = "0")
        assertColumn("user_chat_statistics", "total_length", "BIGINT", nullable = false, defaultValue = "0")
        assertColumn("user_chat_statistics", "last_message_at", "TIMESTAMP WITH TIME ZONE", nullable = false)
    }

    private fun assertUserHourStatisticsColumns() {
        assertColumn("user_hour_statistics", "user_id", "BIGINT", nullable = false, primaryKey = true)
        assertColumn("user_hour_statistics", "utc_date", "DATE", nullable = false, primaryKey = true)
        assertColumn("user_hour_statistics", "utc_hour", "SMALLINT", nullable = false, primaryKey = true)
        assertColumn("user_hour_statistics", "message_count", "BIGINT", nullable = false, defaultValue = "0")
    }

    private fun assertColumn(
        tableName: String,
        columnName: String,
        type: String,
        nullable: Boolean,
        primaryKey: Boolean? = null,
        defaultValue: String? = null,
        defaultValueComputed: String? = null,
    ) {
        val column = column(tableName, columnName)
        val location = "$tableName.$columnName"
        assertEquals(columnName, column.name, location)
        assertEquals(type, column.type, location)
        assertEquals(nullable, column.constraints.isNullable, location)
        assertEquals(primaryKey, column.constraints.isPrimaryKey, location)
        assertEquals(defaultValue, column.defaultValue, location)
        assertEquals(defaultValueComputed, column.defaultValueComputed?.value, location)
    }

    private fun assertNoDataChanges(changes: List<Change>) {
        val dataChanges = changes.filter {
            it is InsertDataChange || it is DeleteDataChange || it is UpdateDataChange || it is LoadDataChange
        }

        assertEquals(emptyList<Change>(), dataChanges)
        changes.filterIsInstance<RawSQLChange>().forEach { change ->
            assertFalse(
                DML_PATTERN.containsMatchIn(change.sql),
                "the changeset sql must not perform DML: ${change.sql}",
            )
        }
    }

    private fun column(tableName: String, columnName: String): ColumnConfig = databaseChangeLog()
        .changeSets
        .flatMap { it.changes }
        .filterIsInstance<CreateTableChange>()
        .single { it.tableName == tableName }
        .columns
        .single { it.name == columnName }

    private fun changeSet(): ChangeSet = databaseChangeLog()
        .changeSets
        .single { it.id == CHANGE_SET_ID }

    private fun databaseChangeLog(): DatabaseChangeLog = ChangeLogParserFactory.getInstance()
        .getParser(CHANGELOG, ClassLoaderResourceAccessor())
        .parse(CHANGELOG, ChangeLogParameters(), ClassLoaderResourceAccessor())

    private fun masterText(): String = ClassLoaderResourceAccessor().get(MASTER)
        .openInputStream()
        .bufferedReader()
        .use { it.readText() }

    private fun eightChangesetFile(): File = projectRoot()
        .resolve("src/persistence/src/main/resources/db/changelog/changes")
        .resolve(EIGHT_CHANGELOG)
        .toFile()

    private fun projectRoot(): Path {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (current.parent != null && !current.resolve("settings.gradle.kts").toFile().isFile) {
            current = current.parent
        }
        return current
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val CHANGE_SET_ID = "009-create-user-statistics-tables"
        const val CHANGELOG = "db/changelog/changes/009-create-user-statistics-tables.yaml"
        const val MASTER = "db/changelog/db.changelog-master.yaml"
        const val EIGHT_CHANGELOG = "008-replace-profile-data-with-human-details.yaml"
        const val NINE_CHANGELOG = "009-create-user-statistics-tables.yaml"
        const val EIGHT_CHANGESET_SHA256 = "3e3f88895172f1ca8708999122cbad83d7435e848da7aafaa135fa7e000af971"
        val DML_PATTERN = Regex("\\b(INSERT|UPDATE|DELETE)\\b", RegexOption.IGNORE_CASE)
    }
}
