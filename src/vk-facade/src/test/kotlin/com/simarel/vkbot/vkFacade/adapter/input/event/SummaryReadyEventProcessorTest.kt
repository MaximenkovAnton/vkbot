package com.simarel.vkbot.vkFacade.adapter.input.event

import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import com.simarel.vkbot.testfixtures.port.input.vk.FakeVkSendMessageInputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SummaryReadyEventProcessorTest {

    private val vkSendMessageInputPort = FakeVkSendMessageInputPort()
    private val processor = SummaryReadyEventProcessor(
        objectMapper = ObjectMapperProvider.create(),
        vkSendMessageInputPort = vkSendMessageInputPort,
        botId = BOT_ID,
        groupId = GROUP_ID,
    )

    @Test
    fun `summary ready sends the summary into the chat with the summarized range forwarded`() {
        processor.process(
            """{"peerId":2000000001,"messageText":"📋 Суммаризация обсуждения",""" +
                """"firstConversationMessageId":10,"lastConversationMessageId":20}""",
        )

        val request = vkSendMessageInputPort.executeCalls.single()
        val summaryMessage = request.responseMessage.responseTo
        assertEquals(PEER_ID, summaryMessage.peerId.value)
        assertEquals("📋 Суммаризация обсуждения", request.responseMessage.messageText.value)
        assertEquals(BOT_ID, summaryMessage.fromId.value)
        assertEquals(GROUP_ID, summaryMessage.groupId.value)
        assertEquals(LAST_MESSAGE_ID, summaryMessage.conversationMessageId.value)
        val forwarded = requireNotNull(request.forwardedMessages)
        assertEquals(PEER_ID, forwarded.peer_id)
        assertEquals(listOf(FIRST_MESSAGE_ID, LAST_MESSAGE_ID), forwarded.conversation_message_ids)
        assertFalse(forwarded.is_reply)
    }

    private companion object {
        const val PEER_ID = 2000000001L
        const val FIRST_MESSAGE_ID = 10L
        const val LAST_MESSAGE_ID = 20L
        const val BOT_ID = 123456789L
        const val GROUP_ID = 232142875L
    }
}
