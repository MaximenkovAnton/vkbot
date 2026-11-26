package com.simarel.vkbot.persistence.adapter.output.persistence.migration

import liquibase.change.Change
import liquibase.change.core.AddColumnChange
import liquibase.change.core.AddUniqueConstraintChange
import liquibase.change.core.CreateIndexChange
import liquibase.change.core.CreateTableChange
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.parser.ChangeLogParserFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

class InboxMigrationChangesetTest {

    @Test
    fun `changeset 011 declares incoming_messages and the messages inbox id`() {
        val changes = changeSet(INCOMING_MESSAGES_CHANGE_SET).changes

        assertIncomingMessagesTable(changes)
        assertIncomingMessagesIndexes(changes)
        assertMessagesInboxColumn(changes)
        assertFalse(changes.any { it is liquibase.change.core.AddForeignKeyConstraintChange })
    }

    @Test
    fun `changeset 012 declares long_poll_lease`() {
        val changes = changeSet(LONG_POLL_LEASE_CHANGE_SET).changes

        assertEquals(listOf(CreateTableChange::class.java), changes.map { it.javaClass })
        val table = changes.filterIsInstance<CreateTableChange>().single()
        assertEquals("long_poll_lease", table.tableName)
        assertEquals(
            listOf("id", "owner", "lease_until"),
            table.columns.map { it.name },
        )
        assertColumn(table, "id", "SMALLINT", nullable = false, primaryKey = true)
        assertColumn(table, "owner", "VARCHAR(64)", nullable = false)
        assertColumn(table, "lease_until", "TIMESTAMP WITH TIME ZONE", nullable = false)
    }

    @Test
    fun `master changelog includes 011 and 012 after 010`() {
        val master = masterText()

        assertTrue(master.indexOf(TEN_CHANGELOG) < master.indexOf(ELEVEN_CHANGELOG))
        assertTrue(master.indexOf(ELEVEN_CHANGELOG) < master.indexOf(TWELVE_CHANGELOG))
    }

    @Test
    fun `changeset 010 bytes are unchanged`() {
        val file = changesetsDir().resolve("010-create-user-knowledge-tables.yaml").toFile()

        assertTrue(file.isFile)
        assertEquals("505089b0445080950c15d766862483fd31f9cd1d0b7ee863f0a30478c2a5a43a", sha256(file))
    }

    private fun assertIncomingMessagesTable(changes: List<Change>) {
        val table = changes.filterIsInstance<CreateTableChange>().single()
        assertEquals("incoming_messages", table.tableName)
        assertEquals(
            listOf("id", "peer_id", "conversation_message_id", "payload", "received_at"),
            table.columns.map { it.name },
        )
        assertColumn(table, "id", "BIGINT", nullable = false, primaryKey = true, autoIncrement = true)
        assertColumn(table, "peer_id", "BIGINT", nullable = false)
        assertColumn(table, "conversation_message_id", "BIGINT", nullable = false)
        assertColumn(table, "payload", "JSONB", nullable = false)
        assertColumn(
            table,
            "received_at",
            "TIMESTAMP WITH TIME ZONE",
            nullable = false,
            defaultValueComputed = "now()",
        )
    }

    private fun assertIncomingMessagesIndexes(changes: List<Change>) {
        val unique = changes.filterIsInstance<AddUniqueConstraintChange>().single()
        assertEquals("uq_incoming_messages_peer_id_conversation_message_id", unique.constraintName)
        assertEquals("incoming_messages", unique.tableName)
        assertEquals("peer_id, conversation_message_id", unique.columnNames)

        val index = changes.filterIsInstance<CreateIndexChange>().single { it.tableName == "incoming_messages" }
        assertEquals("idx_incoming_messages_received_at", index.indexName)
        assertEquals(listOf("received_at"), index.columns.map { it.name })
    }

    private fun assertMessagesInboxColumn(changes: List<Change>) {
        val addColumn = changes.filterIsInstance<AddColumnChange>().single()
        assertEquals("messages", addColumn.tableName)
        val column = addColumn.columns.single()
        assertEquals("inbox_id", column.name)
        assertEquals("BIGINT", column.type)
        assertEquals(true, column.constraints.isNullable)

        val index = changes.filterIsInstance<CreateIndexChange>().single { it.tableName == "messages" }
        assertEquals("uq_messages_inbox_id", index.indexName)
        assertEquals(true, index.isUnique)
        assertEquals(listOf("inbox_id"), index.columns.map { it.name })
        assertNull(changes.filterIsInstance<AddUniqueConstraintChange>().firstOrNull { it.tableName == "messages" })
    }

    private fun assertColumn(
        table: CreateTableChange,
        columnName: String,
        type: String,
        nullable: Boolean,
        primaryKey: Boolean? = null,
        autoIncrement: Boolean? = null,
        defaultValueComputed: String? = null,
    ) {
        val column = table.columns.single { it.name == columnName }
        val location = "${table.tableName}.$columnName"
        assertEquals(columnName, column.name, location)
        assertEquals(type, column.type, location)
        assertEquals(nullable, column.constraints.isNullable, location)
        assertEquals(primaryKey, column.constraints.isPrimaryKey, location)
        assertEquals(autoIncrement, column.isAutoIncrement, location)
        assertEquals(defaultValueComputed, column.defaultValueComputed?.value, location)
    }

    private fun changeSet(id: String): ChangeSet = databaseChangeLog().changeSets.single { it.id == id }

    private fun databaseChangeLog(): DatabaseChangeLog = ChangeLogParserFactory.getInstance()
        .getParser(MASTER, ClassLoaderResourceAccessor())
        .parse(MASTER, ChangeLogParameters(), ClassLoaderResourceAccessor())

    private fun masterText(): String = ClassLoaderResourceAccessor().get(MASTER)
        .openInputStream()
        .bufferedReader()
        .use { it.readText() }

    private fun changesetsDir(): Path = projectRoot()
        .resolve("src/persistence/src/main/resources/db/changelog/changes")

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
        const val INCOMING_MESSAGES_CHANGE_SET = "011-create-incoming-messages"
        const val LONG_POLL_LEASE_CHANGE_SET = "012-create-long-poll-lease"
        const val MASTER = "db/changelog/db.changelog-master.yaml"
        const val TEN_CHANGELOG = "010-create-user-knowledge-tables.yaml"
        const val ELEVEN_CHANGELOG = "011-create-incoming-messages.yaml"
        const val TWELVE_CHANGELOG = "012-create-long-poll-lease.yaml"
    }
}
