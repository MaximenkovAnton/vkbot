package com.simarel.vkbot.greetings.command.collect

import com.simarel.vkbot.greetings.domain.BirthdayDates
import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.greetings.domain.Holidays
import com.simarel.vkbot.greetings.domain.NameFormatter
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.persistence.FindGroupChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserProfilesByBirthDatePort
import jakarta.enterprise.context.ApplicationScoped
import java.lang.System.Logger.Level
import java.time.LocalDate

interface CollectGreetingPlansCommand :
    Command<
        CollectGreetingPlansCommand.Request,
        CollectGreetingPlansCommand.Response,
        > {
    data class Request(val date: LocalDate) : CommandRequest
    data class Response(val plans: List<GreetingPlan>) : CommandResponse
}

@ApplicationScoped
open class CollectGreetingPlansCommandImpl(
    private val findGroupChatPeerIds: FindGroupChatPeerIdsPort,
    private val findUserChatPeerIds: FindUserChatPeerIdsPort,
    private val findUserProfilesByBirthDate: FindUserProfilesByBirthDatePort,
) : CollectGreetingPlansCommand {
    private val logger = System.getLogger(CollectGreetingPlansCommandImpl::class.java.name)

    override fun execute(request: CollectGreetingPlansCommand.Request): CollectGreetingPlansCommand.Response {
        val holidays = Holidays.forDate(request.date)
        val holidaysByPeer = mutableMapOf<PeerId, MutableSet<Holiday>>()
        val birthdaysByPeer = mutableMapOf<PeerId, MutableSet<String>>()

        if (holidays.isNotEmpty()) {
            findGroupChatPeerIds.execute(FindGroupChatPeerIdsPort.Request).peerIds.forEach { peerId ->
                holidaysByPeer.getOrPut(peerId) { mutableSetOf() }.addAll(holidays)
            }
        }

        BirthdayDates.monthDaysFor(request.date).forEach { monthDay ->
            findUserProfilesByBirthDate.execute(
                FindUserProfilesByBirthDatePort.Request(day = monthDay.dayOfMonth, month = monthDay.monthValue),
            ).profiles.forEach { profile ->
                val name = NameFormatter.format(profile)
                val chats = findUserChatPeerIds.execute(FindUserChatPeerIdsPort.Request(profile.id)).peerIds
                if (chats.isEmpty()) {
                    logger.log(Level.WARNING, "Birthday profile has no chats, skipped: user=${profile.id.value}")
                    return@forEach
                }
                chats.forEach { peerId ->
                    birthdaysByPeer.getOrPut(peerId) { mutableSetOf() }.add(name)
                }
            }
        }

        val plans = (holidaysByPeer.keys + birthdaysByPeer.keys)
            .map { peerId ->
                GreetingPlan(
                    peerId = peerId,
                    holidays = holidaysByPeer[peerId].orEmpty().sortedBy { it.ordinal },
                    birthdays = birthdaysByPeer[peerId].orEmpty().sorted(),
                )
            }
            .sortedBy { it.peerId.value }

        return CollectGreetingPlansCommand.Response(plans)
    }
}
