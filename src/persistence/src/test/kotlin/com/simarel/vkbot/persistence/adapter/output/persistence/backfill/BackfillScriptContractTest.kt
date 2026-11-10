package com.simarel.vkbot.persistence.adapter.output.persistence.backfill

import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackfillScriptContractTest {

    @Test
    fun `backfill script leaves the transaction to the caller`() {
        val script = script()

        assertFalse(
            TRANSACTION_CONTROL_PATTERN.containsMatchIn(script),
            "the script must not begin, commit or roll back a transaction",
        )
        assertTrue(script.startsWith("SET LOCAL lock_timeout = '2s'"))
    }

    @Test
    fun `backfill script locks the three aggregates and analyzes them`() {
        val script = script()
        val statements = statements(script)

        assertTrue(script.contains(LOCK_STATEMENT), "missing lock statement")
        TABLES.forEach { table ->
            assertTrue(script.contains("ANALYZE $table"), "missing ANALYZE for $table")
            assertTrue(script.contains("DELETE FROM $table"), "missing DELETE for $table")
            val inserts = statements.filter { it.startsWithIgnoreCase("INSERT INTO $table") }
            assertEquals(1, inserts.size, "expected one INSERT INTO $table")
            assertTrue(inserts.single().contains("SELECT"), "INSERT INTO $table must select from messages")
        }
        assertEquals(
            3,
            statements.count { it.startsWithIgnoreCase("INSERT INTO ") },
            "expected exactly three INSERT statements",
        )
    }

    @Test
    fun `backfill script contains only delete, insert-select, lock and analyze statements`() {
        val script = script()

        statements(script).forEach { statement ->
            assertTrue(
                ALLOWED_PREFIXES.any { statement.startsWithIgnoreCase(it) },
                "unexpected statement: $statement",
            )
        }
        assertFalse(
            FORBIDDEN_PATTERN.containsMatchIn(script),
            "the script must contain only the five whitelisted statement prefixes",
        )
    }

    private fun statements(script: String): List<String> = script.split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private fun String.startsWithIgnoreCase(prefix: String): Boolean = regionMatches(
        thisOffset = 0,
        other = prefix,
        otherOffset = 0,
        length = prefix.length,
        ignoreCase = true,
    )

    private fun script(): String = ClassLoaderResourceAccessor().get(SCRIPT)
        .openInputStream()
        .bufferedReader()
        .use { it.readText() }

    private companion object {
        const val SCRIPT = "db/backfill/backfill-user-statistics.sql"
        const val LOCK_STATEMENT =
            "LOCK TABLE user_statistics, user_chat_statistics, user_hour_statistics IN ACCESS EXCLUSIVE MODE"
        val TRANSACTION_CONTROL_PATTERN = Regex("\\b(BEGIN|COMMIT|ROLLBACK)\\b", RegexOption.IGNORE_CASE)
        val FORBIDDEN_PATTERN = Regex(
            "\\b(BEGIN|COMMIT|ROLLBACK|UPDATE|ALTER|DROP|CREATE|TRUNCATE)\\b",
            RegexOption.IGNORE_CASE,
        )
        val ALLOWED_PREFIXES = listOf("SET LOCAL ", "LOCK TABLE ", "DELETE FROM ", "INSERT INTO ", "ANALYZE ")
        val TABLES = listOf("user_statistics", "user_chat_statistics", "user_hour_statistics")
    }
}
