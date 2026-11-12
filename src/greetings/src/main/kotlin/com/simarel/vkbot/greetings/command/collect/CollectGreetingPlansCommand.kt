package com.simarel.vkbot.greetings.command.collect

import com.simarel.vkbot.greetings.domain.BirthdayDates
import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.greetings.domain.Holidays
import com.simarel.vkbot.greetings.domain.NameFormatter
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.persistence.FindGroupChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserBirthdayFactsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserProfilesByBirthDatePort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
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

@Suppress("TooGenericExceptionCaught")
@ApplicationScoped
open class CollectGreetingPlansCommandImpl(
    private val findGroupChatPeerIds: FindGroupChatPeerIdsPort,
    private val findUserChatPeerIds: FindUserChatPeerIdsPort,
    private val findUserProfilesByBirthDate: FindUserProfilesByBirthDatePort,
    private val findStoredUserProfiles: FindStoredUserProfilesByIdsPort,
    private val findUserBirthdayFacts: FindUserBirthdayFactsPort,
    @ConfigProperty(name = "greetings.hidden-birthdays.enabled", defaultValue = "true")
    private val hiddenBirthdaysEnabled: Boolean,
) : CollectGreetingPlansCommand {
    private val logger = System.getLogger(CollectGreetingPlansCommandImpl::class.java.name)

    override fun execute(request: CollectGreetingPlansCommand.Request): CollectGreetingPlansCommand.Response {
        val holidays = Holidays.forDate(request.date).sortedBy { it.ordinal }
        val holidaysByPeer = holidaysByPeer(holidays)
        val birthdaysByPeer = birthdaysByPeer(request.date)

        val plans = (holidaysByPeer.keys + birthdaysByPeer.keys)
            .sortedBy { it.value }
            .flatMap { peerId ->
                val holidayGreetings = holidaysByPeer[peerId].orEmpty()
                    .map { holiday -> GreetingPlan.HolidayGreeting(peerId, holiday) }
                val birthdayGreeting = birthdaysByPeer[peerId]
                    ?.let { names -> GreetingPlan.BirthdayGreeting(peerId, names.sorted()) }
                holidayGreetings + listOfNotNull(birthdayGreeting)
            }

        return CollectGreetingPlansCommand.Response(plans)
    }

    private fun holidaysByPeer(holidays: List<Holiday>): Map<PeerId, List<Holiday>> {
        if (holidays.isEmpty()) {
            return emptyMap()
        }
        return findGroupChatPeerIds.execute(FindGroupChatPeerIdsPort.Request).peerIds
            .associateWith { holidays }
    }

    private fun birthdaysByPeer(date: LocalDate): Map<PeerId, Set<String>> {
        val birthdaysByPeer = mutableMapOf<PeerId, MutableSet<String>>()
        val vkUserIds = mutableSetOf<Long>()
        BirthdayDates.monthDaysFor(date).forEach { monthDay ->
            findUserProfilesByBirthDate.execute(
                FindUserProfilesByBirthDatePort.Request(day = monthDay.dayOfMonth, month = monthDay.monthValue),
            ).profiles.forEach { profile ->
                vkUserIds += profile.id.value
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
        if (hiddenBirthdaysEnabled) {
            mergeHiddenBirthdays(date, vkUserIds, birthdaysByPeer)
        }
        return birthdaysByPeer
    }

    private fun mergeHiddenBirthdays(
        date: LocalDate,
        vkUserIds: Set<Long>,
        birthdaysByPeer: MutableMap<PeerId, MutableSet<String>>,
    ) {
        val hiddenByPeer = try {
            hiddenBirthdaysByPeer(date, vkUserIds)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw exception
        } catch (exception: Exception) {
            logger.log(Level.WARNING, "Hidden birthday facts read failed (${exception.javaClass.name})")
            return
        }
        hiddenByPeer.forEach { (peerId, names) ->
            birthdaysByPeer.getOrPut(peerId) { mutableSetOf() }.addAll(names)
        }
    }

    private fun hiddenBirthdaysByPeer(date: LocalDate, vkUserIds: Set<Long>): Map<PeerId, Set<String>> {
        val hiddenByUser = BirthdayDates.monthDaysFor(date)
            .flatMap { monthDay ->
                findUserBirthdayFacts.execute(FindUserBirthdayFactsPort.Request(monthDay)).facts
            }
            .filter { fact -> fact.status == UserFactStatus.CONFIRMED && fact.userId !in vkUserIds }
            .associateBy { fact -> fact.userId }
        if (hiddenByUser.isEmpty()) {
            return emptyMap()
        }
        val profilesById = findStoredUserProfiles.execute(
            FindStoredUserProfilesByIdsPort.Request(hiddenByUser.keys.map { userId -> FromId.of(userId) }),
        ).profiles.associateBy { profile -> profile.id.value }
        val hiddenByPeer = mutableMapOf<PeerId, MutableSet<String>>()
        hiddenByUser.forEach { (userId, _) ->
            val profile = profilesById[userId]
            if (profile == null) {
                logger.log(Level.WARNING, "Hidden birthday profile not found, skipped: user=$userId")
                return@forEach
            }
            if (!profile.birthDate.isNullOrBlank()) {
                return@forEach
            }
            val chats = findUserChatPeerIds.execute(FindUserChatPeerIdsPort.Request(profile.id)).peerIds
            if (chats.isEmpty()) {
                logger.log(Level.WARNING, "Hidden birthday has no chats, skipped: user=$userId")
                return@forEach
            }
            val name = NameFormatter.format(profile)
            chats.forEach { peerId -> hiddenByPeer.getOrPut(peerId) { mutableSetOf() }.add(name) }
        }
        return hiddenByPeer
    }
}
