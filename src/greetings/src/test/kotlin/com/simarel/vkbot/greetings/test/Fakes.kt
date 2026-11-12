package com.simarel.vkbot.greetings.test

import com.simarel.vkbot.greetings.command.collect.CollectGreetingPlansCommand
import com.simarel.vkbot.greetings.command.text.GenerateGreetingTextCommand
import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.port.input.RunDailyGreetingsInputPort
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.ai.GenerateGreetingTextPort
import com.simarel.vkbot.share.port.output.persistence.ClaimDailyGreetingPort
import com.simarel.vkbot.share.port.output.persistence.FindGroupChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserBirthdayFactsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserProfilesByBirthDatePort
import com.simarel.vkbot.share.port.output.vk.SendGreetingMessagePort
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakeGreetingPortProvider
import com.simarel.vkbot.testfixtures.port.FakeProfilePortProvider
import java.time.LocalDate
import java.time.MonthDay
import java.util.concurrent.ConcurrentLinkedQueue

class FakeFindGroupChatPeerIds(
    var peerIds: Set<PeerId> = emptySet(),
) : FindGroupChatPeerIdsPort {
    val executeCalls = ConcurrentLinkedQueue<FindGroupChatPeerIdsPort.Request>()

    override fun execute(request: FindGroupChatPeerIdsPort.Request): FindGroupChatPeerIdsPort.Response {
        executeCalls += request
        return FakeGreetingPortProvider.createFindGroupChatPeerIdsResponse(peerIds = peerIds)
    }
}

class FakeFindUserChatPeerIds(
    private val chats: Map<FromId, Set<PeerId>> = emptyMap(),
) : FindUserChatPeerIdsPort {
    val executeCalls = ConcurrentLinkedQueue<FindUserChatPeerIdsPort.Request>()

    override fun execute(request: FindUserChatPeerIdsPort.Request): FindUserChatPeerIdsPort.Response {
        executeCalls += request
        return FakeGreetingPortProvider.createFindUserChatPeerIdsResponse(peerIds = chats[request.userId].orEmpty())
    }
}

class FakeFindUserProfilesByBirthDate(
    private val responses: MutableList<FindUserProfilesByBirthDatePort.Response> = mutableListOf(),
) : FindUserProfilesByBirthDatePort {
    val executeCalls = ConcurrentLinkedQueue<FindUserProfilesByBirthDatePort.Request>()

    override fun execute(request: FindUserProfilesByBirthDatePort.Request): FindUserProfilesByBirthDatePort.Response {
        executeCalls += request
        if (responses.isEmpty()) {
            return FakeGreetingPortProvider.createFindUserProfilesByBirthDateResponse(profiles = emptyList())
        }
        return responses.removeAt(0)
    }
}

class FakeFindUserBirthdayFacts(
    private val factsByMonthDay: Map<MonthDay, List<UserFact>> = emptyMap(),
    var failure: Exception? = null,
) : FindUserBirthdayFactsPort {
    val executeCalls = ConcurrentLinkedQueue<FindUserBirthdayFactsPort.Request>()

    override fun execute(request: FindUserBirthdayFactsPort.Request): FindUserBirthdayFactsPort.Response {
        executeCalls += request
        failure?.let { throw it }
        return FakeGreetingPortProvider.createFindUserBirthdayFactsResponse(
            facts = factsByMonthDay[request.monthDay].orEmpty(),
        )
    }
}

class FakeFindStoredUserProfilesByIds(
    private val profiles: List<VkUserProfile> = emptyList(),
    var failure: Exception? = null,
) : FindStoredUserProfilesByIdsPort {
    val executeCalls = ConcurrentLinkedQueue<FindStoredUserProfilesByIdsPort.Request>()

    override fun execute(request: FindStoredUserProfilesByIdsPort.Request): FindStoredUserProfilesByIdsPort.Response {
        executeCalls += request
        failure?.let { throw it }
        val requested = request.ids.mapTo(mutableSetOf()) { it.value }
        return FakeProfilePortProvider.createFindStoredUserProfilesByIdsResponse(
            profiles = profiles.filter { profile -> profile.id.value in requested },
        )
    }
}

class FakeClaimDailyGreeting(
    vararg initiallyClaimed: Pair<LocalDate, PeerId>,
) : ClaimDailyGreetingPort {
    val executeCalls = ConcurrentLinkedQueue<ClaimDailyGreetingPort.Request>()
    val failures = mutableMapOf<PeerId, Exception>()
    private val claimed = mutableSetOf<Pair<LocalDate, PeerId>>()

    init {
        claimed += initiallyClaimed
    }

    override fun execute(request: ClaimDailyGreetingPort.Request): ClaimDailyGreetingPort.Response {
        executeCalls += request
        failures[request.peerId]?.let { throw it }
        return FakeGreetingPortProvider.createClaimDailyGreetingResponse(
            claimed = claimed.add(request.date to request.peerId),
        )
    }
}

class FakeGenerateGreetingTextPort(
    var response: GenerateGreetingTextPort.Response = FakeGreetingPortProvider.createGenerateGreetingTextResponse(),
    var failure: Exception? = null,
) : GenerateGreetingTextPort {
    val executeCalls = ConcurrentLinkedQueue<GenerateGreetingTextPort.Request>()

    override fun execute(request: GenerateGreetingTextPort.Request): GenerateGreetingTextPort.Response {
        executeCalls += request
        failure?.let { throw it }
        return response
    }
}

class FakeGenerateGreetingTextCommand(
    private val text: MessageText = FakeVoProvider.createMessageText(),
    private val failure: Exception? = null,
) : GenerateGreetingTextCommand {
    val executeCalls = ConcurrentLinkedQueue<GenerateGreetingTextCommand.Request>()

    override fun execute(request: GenerateGreetingTextCommand.Request): GenerateGreetingTextCommand.Response {
        executeCalls += request
        failure?.let { throw it }
        return FakeGreetingProvider.createGenerateGreetingTextCommandResponse(text = text)
    }
}

class FakeCollectGreetingPlansCommand(
    var plans: List<GreetingPlan> = emptyList(),
) : CollectGreetingPlansCommand {
    val executeCalls = ConcurrentLinkedQueue<CollectGreetingPlansCommand.Request>()

    override fun execute(request: CollectGreetingPlansCommand.Request): CollectGreetingPlansCommand.Response {
        executeCalls += request
        return FakeGreetingProvider.createCollectGreetingPlansResponse(plans = plans)
    }
}

class FakeSendGreetingMessage : SendGreetingMessagePort {
    val executeCalls = ConcurrentLinkedQueue<SendGreetingMessagePort.Request>()
    val failures = mutableMapOf<PeerId, Exception>()
    val failuresByAttempt = mutableMapOf<Int, Exception>()

    override fun execute(request: SendGreetingMessagePort.Request): SendGreetingMessagePort.Response {
        executeCalls += request
        failuresByAttempt[executeCalls.size]?.let { throw it }
        failures[request.peerId]?.let { throw it }
        return FakeGreetingPortProvider.createSendGreetingMessageResponse()
    }
}

class FakeRunDailyGreetingsInputPort(
    private val handler: (RunDailyGreetingsInputPort.Request) -> RunDailyGreetingsInputPort.Response =
        { FakeGreetingProvider.createRunDailyGreetingsResponse() },
) : RunDailyGreetingsInputPort {
    val executeCalls = ConcurrentLinkedQueue<RunDailyGreetingsInputPort.Request>()

    val dates: List<LocalDate>
        get() = executeCalls.map { it.date }

    override fun execute(request: RunDailyGreetingsInputPort.Request): RunDailyGreetingsInputPort.Response {
        executeCalls += request
        return handler(request)
    }
}
