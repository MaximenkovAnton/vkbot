package com.simarel.vkbot.ai.testfixtures

import com.simarel.vkbot.ai.command.profile.ProcessProfileCommand
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPortRequest
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPortResponse
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationRequest
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationResponse
import com.simarel.vkbot.share.domain.model.ChatAuthor
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.output.persistence.FindChatAuthorsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfileHistoryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserStatisticsPort
import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider

object FakeProfileProvider {

    fun createProcessProfileInputPortRequest(
        message: Message = FakeVoProvider.createMessage(),
    ) = ProcessProfileInputPortRequest(message)

    fun createProcessProfileInputPortResponse() = ProcessProfileInputPortResponse

    fun createProcessProfileCommandRequest(
        message: Message = FakeVoProvider.createMessage(),
    ) = ProcessProfileCommand.ProcessProfileRequest(message)

    fun createProfileAnswerGenerationRequest(
        question: String = "что ты знаешь о Паше Селезневе",
        data: String = "Профиль:\nИмя: Паша Селезнев",
    ) = ProfileAnswerGenerationRequest(question = question, data = data)

    fun createProfileAnswerGenerationResponse(
        text: String = "profile answer",
    ) = ProfileAnswerGenerationResponse(text)

    fun createFindChatAuthorsResponse(
        authors: List<ChatAuthor> = emptyList(),
    ) = FindChatAuthorsPort.Response(authors)

    fun createFindStoredUserProfilesByIdsResponse(
        profiles: List<VkUserProfile> = emptyList(),
    ) = FindStoredUserProfilesByIdsPort.Response(profiles)

    fun createFindStoredGroupProfilesByIdsResponse(
        profiles: List<VkGroupProfile> = emptyList(),
    ) = FindStoredGroupProfilesByIdsPort.Response(profiles)

    fun createFindStoredUserStatisticsResponse(
        statistics: UserStatistics = FakeModelProvider.createUserStatistics(),
    ) = FindStoredUserStatisticsPort.Response(statistics)

    fun createFindStoredUserProfileHistoryResponse(
        entries: List<ProfileHistoryEntry> = emptyList(),
    ) = FindStoredUserProfileHistoryPort.Response(entries)

    fun createGetUserProfilesBatchResponse(
        profiles: List<VkUserProfile> = emptyList(),
    ) = GetUserProfilesBatchPort.Response(profiles)

    fun createGetGroupProfilesBatchResponse(
        profiles: List<VkGroupProfile> = emptyList(),
    ) = GetGroupProfilesBatchPort.Response(profiles)
}
