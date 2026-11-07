package com.simarel.vkbot.testfixtures.port

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.ai.GenerateGreetingTextPort
import com.simarel.vkbot.share.port.output.persistence.ClaimDailyGreetingPort
import com.simarel.vkbot.share.port.output.persistence.FindGroupChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserProfilesByBirthDatePort
import com.simarel.vkbot.share.port.output.vk.SendGreetingMessagePort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import java.time.LocalDate

private val GREETING_DATE: LocalDate = LocalDate.parse("2026-09-13")
private const val PEER_ID: Long = 2_000_000_001
private const val USER_ID: Long = 1_234_567
private const val GREETING_TEXT: String = "С Новым годом!"

object FakeGreetingPortProvider {

    fun createFindUserProfilesByBirthDateRequest(
        day: Int = 12,
        month: Int = 9,
    ) = FindUserProfilesByBirthDatePort.Request(day = day, month = month)

    fun createFindUserProfilesByBirthDateResponse(
        profiles: List<VkUserProfile> = listOf(FakeModelProvider.createVkUserProfile()),
    ) = FindUserProfilesByBirthDatePort.Response(profiles = profiles)

    fun createFindUserChatPeerIdsRequest(
        userId: FromId = FromId.of(USER_ID),
    ) = FindUserChatPeerIdsPort.Request(userId = userId)

    fun createFindUserChatPeerIdsResponse(
        peerIds: Set<PeerId> = setOf(PeerId.of(PEER_ID)),
    ) = FindUserChatPeerIdsPort.Response(peerIds = peerIds)

    fun createFindGroupChatPeerIdsRequest() = FindGroupChatPeerIdsPort.Request

    fun createFindGroupChatPeerIdsResponse(
        peerIds: Set<PeerId> = setOf(PeerId.of(PEER_ID)),
    ) = FindGroupChatPeerIdsPort.Response(peerIds = peerIds)

    fun createClaimDailyGreetingRequest(
        date: LocalDate = GREETING_DATE,
        peerId: PeerId = PeerId.of(PEER_ID),
    ) = ClaimDailyGreetingPort.Request(date = date, peerId = peerId)

    fun createClaimDailyGreetingResponse(
        claimed: Boolean = true,
    ) = ClaimDailyGreetingPort.Response(claimed = claimed)

    fun createGenerateGreetingTextRequest(
        date: LocalDate = GREETING_DATE,
        holidays: List<String> = listOf("День программиста"),
        people: List<String> = listOf("Иван Петров"),
    ) = GenerateGreetingTextPort.Request(date = date, holidays = holidays, people = people)

    fun createGenerateGreetingTextResponse(
        text: MessageText = MessageText.of(GREETING_TEXT),
    ) = GenerateGreetingTextPort.Response(text = text)

    fun createSendGreetingMessageRequest(
        peerId: PeerId = PeerId.of(PEER_ID),
        messageText: MessageText = MessageText.of(GREETING_TEXT),
    ) = SendGreetingMessagePort.Request(peerId = peerId, messageText = messageText)

    fun createSendGreetingMessageResponse() = SendGreetingMessagePort.Response
}
