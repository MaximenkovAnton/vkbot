package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.testfixtures.FakeUserStatisticsWriterRepository
import com.simarel.vkbot.persistence.testfixtures.StatisticsPortsProvider
import com.simarel.vkbot.persistence.testfixtures.UserStatisticsWriterCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

private const val USER_ID: Long = 7_610_002L
private const val PEER_ID: Long = 111L
private const val MESSAGE_TEXT_LENGTH: Int = 5
private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-05T10:00:00+03:00")

class UpdateUserStatisticsAdapterTest {

    @Test
    fun `execute writes totals and chat and hour in that order`() {
        val repository = FakeUserStatisticsWriterRepository()
        val adapter = UpdateUserStatisticsAdapter(repository)

        adapter.execute(
            StatisticsPortsProvider.createUpdateUserStatisticsRequest(
                userId = USER_ID,
                peerId = PEER_ID,
                date = FIXED_DATE,
                messageTextLength = MESSAGE_TEXT_LENGTH,
            ),
        )

        assertEquals(listOf("totals", "chat", "hour"), repository.recordedCalls.map { it.operation })
        assertEquals(
            listOf(
                UserStatisticsWriterCall(
                    operation = "totals",
                    userId = USER_ID,
                    date = FIXED_DATE,
                    messageTextLength = MESSAGE_TEXT_LENGTH,
                ),
                UserStatisticsWriterCall(
                    operation = "chat",
                    userId = USER_ID,
                    peerId = PEER_ID,
                    date = FIXED_DATE,
                    messageTextLength = MESSAGE_TEXT_LENGTH,
                ),
                UserStatisticsWriterCall(
                    operation = "hour",
                    userId = USER_ID,
                    date = FIXED_DATE,
                ),
            ),
            repository.recordedCalls.toList(),
        )
    }
}
