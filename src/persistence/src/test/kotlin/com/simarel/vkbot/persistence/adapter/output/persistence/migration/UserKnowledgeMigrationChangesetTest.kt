package com.simarel.vkbot.persistence.adapter.output.persistence.migration

import liquibase.change.Change
import liquibase.change.ColumnConfig
import liquibase.change.core.AddForeignKeyConstraintChange
import liquibase.change.core.AddUniqueConstraintChange
import liquibase.change.core.CreateIndexChange
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

class UserKnowledgeMigrationChangesetTest {

    @Test
    fun `createsKnowledgeTablesWithoutDataChanges`() {
        val changes = changeSet().changes

        assertEquals(
            listOf(
                CreateTableChange::class.java,
                CreateTableChange::class.java,
                CreateTableChange::class.java,
                CreateTableChange::class.java,
                CreateTableChange::class.java,
                AddUniqueConstraintChange::class.java,
                AddUniqueConstraintChange::class.java,
                CreateIndexChange::class.java,
                CreateIndexChange::class.java,
                AddForeignKeyConstraintChange::class.java,
                RawSQLChange::class.java,
                RawSQLChange::class.java,
            ),
            changes.map { it.javaClass },
        )
        assertEquals(
            listOf(
                "user_facts",
                "user_fact_evidence",
                "user_knowledge_summary",
                "chat_knowledge_summary",
                "knowledge_extraction_cursors",
            ),
            changes.filterIsInstance<CreateTableChange>().map { it.tableName },
        )
        assertNoDataChanges(changes)
        assertConstraints(changes)
        assertForeignKey(changes)
        assertCheckConstraints(changes)
    }

    @Test
    fun `createsUserFactsColumns`() {
        assertColumn("user_facts", "id", "UUID", nullable = false, primaryKey = true)
        assertColumn("user_facts", "user_id", "BIGINT", nullable = false)
        assertColumn("user_facts", "peer_id", "BIGINT", nullable = false)
        assertColumn("user_facts", "category", "VARCHAR(24)", nullable = false)
        assertColumn("user_facts", "fact_key", "VARCHAR(96)", nullable = false)
        assertColumn("user_facts", "value", "TEXT", nullable = false)
        assertColumn("user_facts", "value_hash", "CHAR(64)", nullable = false)
        assertColumn("user_facts", "value_payload", "JSONB", nullable = true)
        assertColumn("user_facts", "evidence_count", "INT", nullable = false, defaultValue = "0")
        assertColumn("user_facts", "max_evidence_confidence", "NUMERIC(4,3)", nullable = false, defaultValue = "0")
        assertColumn("user_facts", "confidence", "NUMERIC(4,3)", nullable = false)
        assertColumn("user_facts", "status", "VARCHAR(16)", nullable = false)
        assertColumn("user_facts", "first_observed_at", "TIMESTAMP WITH TIME ZONE", nullable = false)
        assertColumn("user_facts", "last_confirmed_at", "TIMESTAMP WITH TIME ZONE", nullable = false)
        assertColumn("user_facts", "superseded_at", "TIMESTAMP WITH TIME ZONE", nullable = true)
        assertColumn("user_facts", "conflict", "BOOLEAN", nullable = false, defaultValue = "false")
        assertColumn(
            "user_facts",
            "created_at",
            "TIMESTAMP WITH TIME ZONE",
            nullable = false,
            defaultValueComputed = "now()",
        )
        assertColumn(
            "user_facts",
            "updated_at",
            "TIMESTAMP WITH TIME ZONE",
            nullable = false,
            defaultValueComputed = "now()",
        )
    }

    @Test
    fun `createsRemainingKnowledgeTablesColumns`() {
        assertColumn("user_fact_evidence", "id", "UUID", nullable = false, primaryKey = true)
        assertColumn("user_fact_evidence", "fact_id", "UUID", nullable = false)
        assertColumn("user_fact_evidence", "message_id", "UUID", nullable = false)
        assertColumn("user_fact_evidence", "conversation_message_id", "BIGINT", nullable = false)
        assertColumn("user_fact_evidence", "peer_id", "BIGINT", nullable = false)
        assertColumn("user_fact_evidence", "said_by_id", "BIGINT", nullable = false)
        assertColumn("user_fact_evidence", "source_kind", "VARCHAR(24)", nullable = false)
        assertColumn("user_fact_evidence", "confidence", "NUMERIC(4,3)", nullable = false)
        assertColumn("user_fact_evidence", "observed_at", "TIMESTAMP WITH TIME ZONE", nullable = false)

        assertColumn("user_knowledge_summary", "user_id", "BIGINT", nullable = false, primaryKey = true)
        assertColumn("user_knowledge_summary", "summary_text", "TEXT", nullable = false)
        assertColumn("user_knowledge_summary", "facts_covered", "INT", nullable = false)
        assertColumn("user_knowledge_summary", "last_fact_at", "TIMESTAMP WITH TIME ZONE", nullable = false)
        assertColumn("user_knowledge_summary", "summarized_at", "TIMESTAMP WITH TIME ZONE", nullable = false)

        assertColumn("chat_knowledge_summary", "peer_id", "BIGINT", nullable = false, primaryKey = true)
        assertColumn("chat_knowledge_summary", "summary_text", "TEXT", nullable = false)
        assertColumn("chat_knowledge_summary", "messages_covered", "INT", nullable = false)
        assertColumn(
            "chat_knowledge_summary",
            "first_conversation_message_id",
            "BIGINT",
            nullable = false,
        )
        assertColumn(
            "chat_knowledge_summary",
            "last_conversation_message_id",
            "BIGINT",
            nullable = false,
        )
        assertColumn("chat_knowledge_summary", "summarized_at", "TIMESTAMP WITH TIME ZONE", nullable = false)

        assertColumn("knowledge_extraction_cursors", "peer_id", "BIGINT", nullable = false, primaryKey = true)
        assertColumn(
            "knowledge_extraction_cursors",
            "last_conversation_message_id",
            "BIGINT",
            nullable = false,
        )
        assertColumn(
            "knowledge_extraction_cursors",
            "last_completed_at",
            "TIMESTAMP WITH TIME ZONE",
            nullable = false,
        )
        assertColumn("knowledge_extraction_cursors", "updated_at", "TIMESTAMP WITH TIME ZONE", nullable = false)
    }

    @Test
    fun `createsMessagesKnowledgeIndexConcurrently`() {
        val indexChangeSet = databaseChangeLog().changeSets.single { it.id == INDEX_CHANGE_SET_ID }

        assertFalse(indexChangeSet.isRunInTransaction)
        assertEquals(
            listOf(RawSQLChange::class.java, RawSQLChange::class.java),
            indexChangeSet.changes.map { it.javaClass },
        )
        val sql = indexChangeSet.changes.filterIsInstance<RawSQLChange>().map { it.sql }
        assertEquals(
            listOf(
                "DROP INDEX CONCURRENTLY IF EXISTS idx_messages_peer_id_conversation_message_id",
                "CREATE INDEX CONCURRENTLY idx_messages_peer_id_conversation_message_id " +
                    "ON messages (peer_id, conversation_message_id)",
            ),
            sql,
        )
    }

    @Test
    fun `appliedChangesetsKeepBytes`() {
        APPLIED_CHANGESETS.forEach { (fileName, expectedSha) ->
            val file = changesetsDir().resolve(fileName).toFile()
            assertTrue(file.isFile, "missing changeset file: $file")
            assertEquals(expectedSha, sha256(file), fileName)
        }
    }

    @Test
    fun `masterIncludesKnowledgeChangesetAfterStatistics`() {
        val master = masterText()

        assertTrue(master.indexOf(NINE_CHANGELOG) < master.indexOf(TEN_CHANGELOG))
        assertTrue(master.contains(TEN_CHANGELOG))
    }

    private fun assertConstraints(changes: List<Change>) {
        val unique = changes.filterIsInstance<AddUniqueConstraintChange>().associateBy { it.constraintName }
        assertEquals(
            setOf(
                "uq_user_facts_user_category_fact_key_value_hash",
                "uq_user_fact_evidence_fact_peer_conversation",
            ),
            unique.keys,
        )
        assertEquals("user_facts", unique.getValue("uq_user_facts_user_category_fact_key_value_hash").tableName)
        assertEquals(
            "user_id, category, fact_key, value_hash",
            unique.getValue("uq_user_facts_user_category_fact_key_value_hash").columnNames,
        )
        assertEquals(
            "fact_id, peer_id, conversation_message_id",
            unique.getValue("uq_user_fact_evidence_fact_peer_conversation").columnNames,
        )

        val indexes = changes.filterIsInstance<CreateIndexChange>().associateBy { it.indexName }
        assertEquals(
            setOf(
                "idx_user_facts_user_id_status",
                "idx_user_facts_user_id_fact_key_last_confirmed_at",
            ),
            indexes.keys,
        )
        assertEquals(
            listOf("user_id", "status"),
            indexes.getValue("idx_user_facts_user_id_status").columns.map { it.name },
        )
        assertEquals(
            listOf("user_id", "fact_key", "last_confirmed_at"),
            indexes.getValue("idx_user_facts_user_id_fact_key_last_confirmed_at").columns.map { it.name },
        )
    }

    private fun assertForeignKey(changes: List<Change>) {
        val foreignKey = changes.filterIsInstance<AddForeignKeyConstraintChange>().single()

        assertEquals("fk_user_fact_evidence_fact", foreignKey.constraintName)
        assertEquals("user_fact_evidence", foreignKey.baseTableName)
        assertEquals("fact_id", foreignKey.baseColumnNames)
        assertEquals("user_facts", foreignKey.referencedTableName)
        assertEquals("id", foreignKey.referencedColumnNames)
        assertEquals("CASCADE", foreignKey.onDelete)
    }

    private fun assertCheckConstraints(changes: List<Change>) {
        assertEquals(
            listOf(
                "ALTER TABLE user_facts ADD CONSTRAINT chk_user_facts_confidence " +
                    "CHECK (confidence BETWEEN 0 AND 1)",
                "ALTER TABLE user_facts ADD CONSTRAINT chk_user_facts_max_evidence_confidence " +
                    "CHECK (max_evidence_confidence BETWEEN 0 AND 1)",
            ),
            changes.filterIsInstance<RawSQLChange>().map { it.sql },
        )
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
        const val CHANGE_SET_ID = "010-create-user-knowledge-tables"
        const val INDEX_CHANGE_SET_ID = "010-create-messages-knowledge-index"
        const val CHANGELOG = "db/changelog/changes/010-create-user-knowledge-tables.yaml"
        const val MASTER = "db/changelog/db.changelog-master.yaml"
        const val NINE_CHANGELOG = "009-create-user-statistics-tables.yaml"
        const val TEN_CHANGELOG = "010-create-user-knowledge-tables.yaml"
        val DML_PATTERN = Regex("\\b(INSERT|UPDATE|DELETE)\\b", RegexOption.IGNORE_CASE)
        val APPLIED_CHANGESETS = listOf(
            "001-create-messages-table.yaml" to
                "9fd47e7fd9699a4f5c3ca94113bd7d7edf67416609abb9adbec092a5238fd954",
            "002-create-user-profiles-table.yaml" to
                "5d811cbfcce619b230e2e5b53291d26897768755263334ca294c95627b2ccc31",
            "003-create-group-profiles-table.yaml" to
                "101d7a5b7c1e0b418edfb938b293126a1fc7b1136c02a524b188ea6718d0db9f",
            "004-create-summaries-table.yaml" to
                "56295cb8e05f5431aa99932cd0c741a83958fb0c97a5d5d55eedbd4c4b6b016a",
            "005-create-long-poll-position-table.yaml" to
                "b2af5f3672484ee3e487e0b3a77f18c295f8ed0b09f9fded6167a2efa7452b87",
            "006-create-greeting-log-table.yaml" to
                "74b750f4d52afd1854eaaed92864ce903d3e3a19201af6f7c928b4f2f6af273d",
            "007-add-user-profile-data-and-history.yaml" to
                "1ace0d330c0567f820ce0300e664ca16240dbda150e44b84536e980b3b8dc5ce",
            "008-replace-profile-data-with-human-details.yaml" to
                "3e3f88895172f1ca8708999122cbad83d7435e848da7aafaa135fa7e000af971",
            "009-create-user-statistics-tables.yaml" to
                "4c245cba72caab5d0aaccbb19098aebd2c1ec3ef5c8e953d28834520316d0516",
        )
    }
}
