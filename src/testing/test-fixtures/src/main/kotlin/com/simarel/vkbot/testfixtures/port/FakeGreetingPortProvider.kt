package com.simarel.vkbot.testfixtures.port

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.ai.GenerateGreetingTextPort
import com.simarel.vkbot.share.port.output.persistence.ClaimDailyGreetingPort
import com.simarel.vkbot.share.port.output.persistence.FindGroupChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserBirthdayFactsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserProfilesByBirthDatePort
import com.simarel.vkbot.share.port.output.vk.SendGreetingMessagePort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import java.time.LocalDate
import java.time.MonthDay

private val GREETING_DATE: LocalDate = LocalDate.parse("2026-09-13")
private const val PEER_ID: Long = 2_000_000_001
private const val USER_ID: Long = 1_234_567
private const val GREETING_TEXT: String = "С Новым годом!"
private const val DEFAULT_BIRTHDAY_MONTH = 5
private const val DEFAULT_BIRTHDAY_DAY = 14

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
        occasion: String = "День программиста",
        people: List<String> = emptyList(),
    ) = GenerateGreetingTextPort.Request(date = date, occasion = occasion, people = people)

    fun createGenerateGreetingTextResponse(
        text: MessageText = MessageText.of(GREETING_TEXT),
    ) = GenerateGreetingTextPort.Response(text = text)

    fun createSendGreetingMessageRequest(
        peerId: PeerId = PeerId.of(PEER_ID),
        messageText: MessageText = MessageText.of(GREETING_TEXT),
    ) = SendGreetingMessagePort.Request(peerId = peerId, messageText = messageText)

    fun createSendGreetingMessageResponse() = SendGreetingMessagePort.Response

    fun createFindUserBirthdayFactsRequest(
        monthDay: MonthDay = MonthDay.of(DEFAULT_BIRTHDAY_MONTH, DEFAULT_BIRTHDAY_DAY),
    ) = FindUserBirthdayFactsPort.Request(monthDay = monthDay)

    fun createFindUserBirthdayFactsResponse(
        facts: List<UserFact> = emptyList(),
    ) = FindUserBirthdayFactsPort.Response(facts = facts)
}
