package com.simarel.vkbot.ai.testfixtures

import com.simarel.vkbot.ai.command.news.ProcessNewsDigestCommand
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPortRequest
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPortResponse
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationRequest
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationResponse
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredMessageByAuthorPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenDatesPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredSummariesInPeriodPort
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider

object FakeNewsDigestProvider {

    fun createFindLastStoredMessageByAuthorResponse(
        message: StoredMessage? = null,
    ) = FindLastStoredMessageByAuthorPort.Response(message)

    fun createFindStoredMessagesBetweenDatesResponse(
        messages: List<StoredMessage> = emptyList(),
    ) = FindStoredMessagesBetweenDatesPort.Response(messages)

    fun createFindStoredSummariesInPeriodResponse(
        summaries: List<Summary> = emptyList(),
    ) = FindStoredSummariesInPeriodPort.Response(summaries)

    fun createNewsDigestGenerationRequest(
        data: String = "digest data",
    ) = NewsDigestGenerationRequest(data)

    fun createNewsDigestGenerationResponse(
        text: String = "digest text",
    ) = NewsDigestGenerationResponse(text)

    fun createProcessNewsDigestCommandRequest(
        message: Message = FakeVoProvider.createMessage(),
    ) = ProcessNewsDigestCommand.ProcessNewsDigestRequest(message)

    fun createProcessNewsDigestInputPortRequest(
        message: Message = FakeVoProvider.createMessage(),
    ) = ProcessNewsDigestInputPortRequest(message)

    fun createProcessNewsDigestInputPortResponse() = ProcessNewsDigestInputPortResponse
}
