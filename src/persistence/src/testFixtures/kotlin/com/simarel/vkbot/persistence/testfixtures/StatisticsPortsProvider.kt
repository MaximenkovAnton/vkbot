package com.simarel.vkbot.persistence.testfixtures

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.UserStatisticsWriter
import com.simarel.vkbot.persistence.command.fetchuserprofile.FetchUserProfileCommand
import com.simarel.vkbot.persistence.command.fetchuserprofile.FetchUserProfileRequest
import com.simarel.vkbot.persistence.command.fetchuserprofile.FetchUserProfileResponse
import com.simarel.vkbot.persistence.port.input.SaveMessageInputPortRequest
import com.simarel.vkbot.persistence.port.output.ForwardedMessagesSerializationPort
import com.simarel.vkbot.persistence.port.output.persistence.SaveMessagePort
import com.simarel.vkbot.persistence.port.output.persistence.UpdateUserStatisticsPort
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue

private const val DEFAULT_USER_ID: Long = 7_610_002L
private const val DEFAULT_PEER_ID: Long = 111L
private const val DEFAULT_MESSAGE_TEXT_LENGTH: Int = 3
private const val DEFAULT_MESSAGE_TEXT: String = "abc"
private val DEFAULT_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-05T10:00:00+03:00")

object StatisticsPortsProvider {

    fun createMessage(
        date: OffsetDateTime = DEFAULT_DATE,
        fromId: Long = DEFAULT_USER_ID,
        peerId: Long = DEFAULT_PEER_ID,
        text: String = DEFAULT_MESSAGE_TEXT,
        forwardedMessages: List<Message> = emptyList(),
    ): Message = FakeMessageProvider.createMessage(
        date = FakeVoProvider.createDate(date),
        fromId = FakeVoProvider.createFromId(fromId),
        peerId = FakeVoProvider.createPeerId(peerId),
        messageText = FakeVoProvider.createMessageText(text),
        forwardedMessages = forwardedMessages,
    )

    fun createSaveMessageRequest(
        message: Message = createMessage(),
    ) = SaveMessageInputPortRequest(message = message)

    fun createUpdateUserStatisticsRequest(
        userId: Long = DEFAULT_USER_ID,
        peerId: Long = DEFAULT_PEER_ID,
        date: OffsetDateTime = DEFAULT_DATE,
        messageTextLength: Int = DEFAULT_MESSAGE_TEXT_LENGTH,
    ) = UpdateUserStatisticsPort.Request(
        userId = userId,
        peerId = peerId,
        date = date,
        messageTextLength = messageTextLength,
    )

    fun createForwardedMessagesSerializationRequest(
        messages: List<Message> = emptyList(),
    ) = ForwardedMessagesSerializationPort.Request(messages = messages)
}

class FakeUpdateUserStatisticsPort(
    private val failure: RuntimeException? = null,
) : UpdateUserStatisticsPort {

    val requests = ConcurrentLinkedQueue<UpdateUserStatisticsPort.Request>()

    override fun execute(request: UpdateUserStatisticsPort.Request): UpdateUserStatisticsPort.Response {
        requests.add(request)
        failure?.let { throw it }
        return UpdateUserStatisticsPort.Response
    }
}

class FakeSaveMessagePort : SaveMessagePort {

    val requests = ConcurrentLinkedQueue<SaveMessagePort.SaveMessageRequest>()

    override fun execute(request: SaveMessagePort.SaveMessageRequest): SaveMessagePort.SaveMessageResponse {
        requests.add(request)
        return SaveMessagePort.SaveMessageResponse()
    }
}

class FakeFetchUserProfileCommand : FetchUserProfileCommand {

    val executeCalls = ConcurrentLinkedQueue<FetchUserProfileRequest>()

    override fun execute(request: FetchUserProfileRequest): FetchUserProfileResponse {
        executeCalls.add(request)
        return FetchUserProfileResponse(success = true)
    }
}

class FakeForwardedMessagesSerializationPort : ForwardedMessagesSerializationPort {

    val requests = ConcurrentLinkedQueue<ForwardedMessagesSerializationPort.Request>()

    override fun execute(
        request: ForwardedMessagesSerializationPort.Request,
    ): ForwardedMessagesSerializationPort.Response {
        requests.add(request)
        return ForwardedMessagesSerializationPort.Response(json = null)
    }
}

class FakeUserStatisticsWriter : UserStatisticsWriter() {

    val recordedCalls = ConcurrentLinkedQueue<UserStatisticsWriterCall>()

    override fun upsertTotals(userId: Long, date: OffsetDateTime, messageTextLength: Int) {
        recordedCalls.add(
            UserStatisticsWriterCall(
                operation = "totals",
                userId = userId,
                date = date,
                messageTextLength = messageTextLength,
            ),
        )
    }

    override fun upsertChat(userId: Long, peerId: Long, date: OffsetDateTime, messageTextLength: Int) {
        recordedCalls.add(
            UserStatisticsWriterCall(
                operation = "chat",
                userId = userId,
                peerId = peerId,
                date = date,
                messageTextLength = messageTextLength,
            ),
        )
    }

    override fun upsertHour(userId: Long, date: OffsetDateTime) {
        recordedCalls.add(
            UserStatisticsWriterCall(
                operation = "hour",
                userId = userId,
                date = date,
            ),
        )
    }
}

data class UserStatisticsWriterCall(
    val operation: String,
    val userId: Long,
    val peerId: Long? = null,
    val date: OffsetDateTime,
    val messageTextLength: Int? = null,
)
