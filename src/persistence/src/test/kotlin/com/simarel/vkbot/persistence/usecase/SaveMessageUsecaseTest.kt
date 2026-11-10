package com.simarel.vkbot.persistence.usecase

import com.simarel.vkbot.persistence.command.fetchuserprofile.FetchUserProfileCommand
import com.simarel.vkbot.persistence.port.output.ForwardedMessagesSerializationPort
import com.simarel.vkbot.persistence.port.output.persistence.SaveMessagePort
import com.simarel.vkbot.persistence.port.output.persistence.UpdateUserStatisticsPort
import com.simarel.vkbot.persistence.testfixtures.FakeFetchUserProfileCommand
import com.simarel.vkbot.persistence.testfixtures.FakeForwardedMessagesSerializationPort
import com.simarel.vkbot.persistence.testfixtures.FakeSaveMessagePort
import com.simarel.vkbot.persistence.testfixtures.FakeUpdateUserStatisticsPort
import com.simarel.vkbot.persistence.testfixtures.StatisticsPortsProvider
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-05T10:00:00+03:00")
private const val USER_ID: Long = 7_610_002L
private const val PEER_ID: Long = 111L
private const val FAILURE_MESSAGE: String = "statistics write failed"

class SaveMessageUsecaseTest {

    @Test
    fun `execute updates statistics with the message length in code points`() {
        val saveMessagePort = FakeSaveMessagePort()
        val statisticsPort = FakeUpdateUserStatisticsPort()
        val usecase = usecase(saveMessagePort = saveMessagePort, statisticsPort = statisticsPort)
        val message = StatisticsPortsProvider.createMessage(
            date = FIXED_DATE,
            fromId = USER_ID,
            peerId = PEER_ID,
            text = "abc",
        )

        usecase.execute(StatisticsPortsProvider.createSaveMessageRequest(message = message))

        assertEquals(
            StatisticsPortsProvider.createUpdateUserStatisticsRequest(
                userId = USER_ID,
                peerId = PEER_ID,
                date = FIXED_DATE,
                messageTextLength = 3,
            ),
            statisticsPort.requests.single(),
        )
        assertEquals(1, saveMessagePort.requests.size)
    }

    @Test
    fun `execute counts an emoji as one code point`() {
        val statisticsPort = FakeUpdateUserStatisticsPort()
        val usecase = usecase(statisticsPort = statisticsPort)

        usecase.execute(
            StatisticsPortsProvider.createSaveMessageRequest(
                message = StatisticsPortsProvider.createMessage(text = "😀"),
            ),
        )

        assertEquals(1, statisticsPort.requests.single().messageTextLength)
    }

    @Test
    fun `execute propagates a statistics write failure`() {
        val usecase = usecase(
            statisticsPort = FakeUpdateUserStatisticsPort(failure = IllegalStateException(FAILURE_MESSAGE)),
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            usecase.execute(StatisticsPortsProvider.createSaveMessageRequest())
        }

        assertEquals(FAILURE_MESSAGE, thrown.message)
    }

    @Test
    fun `execute does not publish events when statistics write fails`() {
        val publishEventCommand = FakePublishEventCommand()
        val usecase = usecase(
            publishEventCommand = publishEventCommand,
            statisticsPort = FakeUpdateUserStatisticsPort(failure = IllegalStateException(FAILURE_MESSAGE)),
        )

        assertThrows(IllegalStateException::class.java) {
            usecase.execute(StatisticsPortsProvider.createSaveMessageRequest())
        }

        assertTrue(publishEventCommand.executeCalls.isEmpty())
    }

    private fun usecase(
        saveMessagePort: SaveMessagePort = FakeSaveMessagePort(),
        forwardedMessagesSerializationPort: ForwardedMessagesSerializationPort =
            FakeForwardedMessagesSerializationPort(),
        publishEventCommand: PublishEventCommand = FakePublishEventCommand(),
        fetchUserProfileCommand: FetchUserProfileCommand = FakeFetchUserProfileCommand(),
        statisticsPort: UpdateUserStatisticsPort = FakeUpdateUserStatisticsPort(),
    ): SaveMessageUsecase = SaveMessageUsecase(
        saveMessagePort = saveMessagePort,
        forwardedMessagesSerializationPort = forwardedMessagesSerializationPort,
        publishEventCommand = publishEventCommand,
        fetchUserProfileCommand = fetchUserProfileCommand,
        updateUserStatisticsPort = statisticsPort,
    )
}
