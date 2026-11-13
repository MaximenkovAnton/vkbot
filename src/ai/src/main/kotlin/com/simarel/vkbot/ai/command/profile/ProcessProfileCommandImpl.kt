package com.simarel.vkbot.ai.command.profile

import com.simarel.vkbot.ai.domain.exception.ProfileGenerationException
import com.simarel.vkbot.ai.domain.profile.ProfileDossier
import com.simarel.vkbot.ai.domain.profile.ProfileDossierFormatter
import com.simarel.vkbot.ai.domain.profile.ProfileNameMatcher
import com.simarel.vkbot.ai.domain.profile.ProfileNameNormalizer
import com.simarel.vkbot.ai.domain.profile.ProfileTarget
import com.simarel.vkbot.ai.domain.profile.ProfileTargetResolver
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationPort
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationRequest
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.ChatAuthor
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.port.output.persistence.FindChatAuthorsPort
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfileHistoryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserStatisticsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.ZoneId

private const val HISTORY_LIMIT = 20
private const val MAX_CLARIFICATION_CANDIDATES = 10
private const val SPACE = " "
private const val LINE_SEPARATOR = "\n"
private const val CANDIDATE_SEPARATOR = "; "
private const val SCREEN_NAME_PREFIX = "@"
private const val ID_HINT_TEMPLATE = "id%d"
private const val PHRASE_PLACEHOLDER = "%s"
private const val PROFILE_UNAVAILABLE_REPLY = "Профиль недоступен."
private const val GENERATION_FAILED_REPLY = "Не удалось собрать профиль. Попробуйте позже."
private const val NO_TARGET_REPLY = "Уточните, о ком речь: имя, @упоминание, id или пересланное сообщение."
private const val CLARIFICATION_HEADER = "Уточните, о ком речь:"
private const val CLARIFICATION_FOOTER = "Напишите полное имя или упоминание."
private const val NOT_FOUND_TEMPLATE = "Среди участников этого чата не нашёл «%s». " +
    "Напишите полное имя, @упоминание, id или перешлите сообщение."
private const val TARGET_ID_LABEL = "targetId"
private const val REQUESTER_ID_LABEL = "requesterId"
private val EMPTY_KNOWLEDGE = UserKnowledge(emptyList(), null, false)

@ApplicationScoped
@Suppress("LongParameterList", "TooManyFunctions")
class ProcessProfileCommandImpl(
    private val publishEventCommand: PublishEventCommand,
    private val findChatAuthorsPort: FindChatAuthorsPort,
    private val findStoredUserProfilesByIdsPort: FindStoredUserProfilesByIdsPort,
    private val findStoredGroupProfilesByIdsPort: FindStoredGroupProfilesByIdsPort,
    private val findStoredUserStatisticsPort: FindStoredUserStatisticsPort,
    private val findStoredUserProfileHistoryPort: FindStoredUserProfileHistoryPort,
    private val findUserKnowledgePort: FindUserKnowledgePort,
    private val findChatKnowledgeSummaryPort: FindChatKnowledgeSummaryPort,
    private val getUserProfilesBatchPort: GetUserProfilesBatchPort,
    private val getGroupProfilesBatchPort: GetGroupProfilesBatchPort,
    private val profileAnswerGenerationPort: ProfileAnswerGenerationPort,
    @ConfigProperty(name = "vk.bot.id")
    private val botId: Long,
    @ConfigProperty(name = "vk.bot.mention", defaultValue = "@simarel")
    private val botMention: String,
    @ConfigProperty(name = "vk.group.id")
    private val groupId: Long,
    @ConfigProperty(name = "statistics.timezone", defaultValue = "Europe/Minsk")
    private val statisticsTimezone: String,
) : ProcessProfileCommand {

    private val logger = System.getLogger(ProcessProfileCommandImpl::class.java.name)
    private val nameNormalizer = ProfileNameNormalizer(botMention)
    private val resolver = ProfileTargetResolver(botId, botMention, groupId, nameNormalizer, ProfileNameMatcher())
    private val formatter = ProfileDossierFormatter(ZoneId.of(statisticsTimezone))

    override fun execute(
        request: ProcessProfileCommand.ProcessProfileRequest,
    ): ProcessProfileCommand.ProcessProfileResponse {
        val message = request.message
        val authors = findChatAuthorsPort.execute(FindChatAuthorsPort.Request(message.peerId)).authors
        when (val resolution = resolve(message, authors)) {
            is ProfileTargetResolver.Resolution.Resolved -> answer(message, resolution, authors)
            is ProfileTargetResolver.Resolution.Ambiguous -> publish(clarification(resolution.targets), message)
            is ProfileTargetResolver.Resolution.NotFound -> publish(notFound(resolution.phrase), message)
            ProfileTargetResolver.Resolution.NoTarget -> publish(NO_TARGET_REPLY, message)
        }
        return ProcessProfileCommand.ProcessProfileResponse
    }

    private fun resolve(message: Message, authors: List<ChatAuthor>): ProfileTargetResolver.Resolution {
        val explicit = resolver.resolveExplicit(message)
        if (explicit != ProfileTargetResolver.Resolution.NoTarget) {
            return explicit
        }
        return resolver.resolveByName(message, participants(authors))
    }

    private fun answer(
        message: Message,
        resolution: ProfileTargetResolver.Resolution.Resolved,
        authors: List<ChatAuthor>,
    ) {
        val chatMessageCount = authors.firstOrNull { it.fromId == resolution.target.fromId }?.messageCount
        val target = resolution.target
        val requesterId = requesterId(message.fromId, target.fromId)
        val dossier = when (target) {
            is ProfileTarget.User -> userDossier(
                target.fromId,
                resolution.profile,
                chatMessageCount,
                message.peerId.value,
                requesterId,
            )
            is ProfileTarget.Group -> groupDossier(target.fromId, chatMessageCount, message.peerId.value, requesterId)
        }
        if (dossier == null) {
            publish(PROFILE_UNAVAILABLE_REPLY, message)
            return
        }
        generate(message, formatter.format(dossier, message.peerId.value))?.let { publish(it, message) }
    }

    private fun requesterId(fromId: FromId, targetId: FromId): FromId? {
        val isRequester = fromId.isHuman() && fromId.value != botId && fromId != targetId
        return fromId.takeIf { isRequester }
    }

    private fun generate(message: Message, data: String): String? = try {
        profileAnswerGenerationPort.execute(
            ProfileAnswerGenerationRequest(message.messageText.value, data),
        ).text
    } catch (e: ProfileGenerationException) {
        logGenerationFailure(message.peerId.value, e)
        publish(GENERATION_FAILED_REPLY, message)
        null
    }

    private fun userDossier(
        userId: FromId,
        resolvedProfile: VkUserProfile?,
        chatMessageCount: Long?,
        peerId: Long,
        requesterId: FromId?,
    ): ProfileDossier? {
        val profile = resolveUserProfile(userId, resolvedProfile) ?: return null
        return ProfileDossier.UserDossier(
            profile = profile,
            statistics = findStoredUserStatisticsPort.execute(
                FindStoredUserStatisticsPort.Request(userId),
            ).statistics,
            history = findStoredUserProfileHistoryPort.execute(
                FindStoredUserProfileHistoryPort.Request(userId, HISTORY_LIMIT),
            ).entries,
            chatMessageCount = chatMessageCount,
            knowledge = knowledge(userId, peerId, TARGET_ID_LABEL),
            chatTopics = chatTopics(peerId),
            requesterProfile = requesterProfile(requesterId, peerId),
            requesterKnowledge = requesterId?.let { knowledge(it, peerId, REQUESTER_ID_LABEL) },
        )
    }

    private fun resolveUserProfile(userId: FromId, resolvedProfile: VkUserProfile?): VkUserProfile? = resolvedProfile
        ?: findStoredUserProfilesByIdsPort.execute(
            FindStoredUserProfilesByIdsPort.Request(listOf(userId)),
        ).profiles.firstOrNull()
        ?: getUserProfilesBatchPort.execute(
            GetUserProfilesBatchPort.Request(listOf(userId)),
        ).profiles.firstOrNull()

    @Suppress("TooGenericExceptionCaught")
    private fun knowledge(userId: FromId, peerId: Long, label: String): UserKnowledge = try {
        findUserKnowledgePort.execute(FindUserKnowledgePort.Request(listOf(userId)))
            .knowledge[userId] ?: EMPTY_KNOWLEDGE
    } catch (e: RuntimeException) {
        logger.log(
            System.Logger.Level.WARNING,
            "Profile knowledge read failed for peerId=$peerId $label=${userId.value}: " +
                e.javaClass.simpleName,
        )
        EMPTY_KNOWLEDGE
    }

    @Suppress("TooGenericExceptionCaught")
    private fun chatTopics(peerId: Long): String? = try {
        findChatKnowledgeSummaryPort.execute(FindChatKnowledgeSummaryPort.Request(peerId))
            .knowledge?.summary?.takeIf(String::isNotBlank)
    } catch (e: RuntimeException) {
        logger.log(
            System.Logger.Level.WARNING,
            "Profile chat knowledge read failed for peerId=$peerId: ${e.javaClass.simpleName}",
        )
        null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun requesterProfile(requesterId: FromId?, peerId: Long): VkUserProfile? {
        if (requesterId == null) {
            return null
        }
        return try {
            storedUserProfiles(listOf(requesterId)).firstOrNull()
        } catch (e: RuntimeException) {
            logger.log(
                System.Logger.Level.WARNING,
                "Profile requester read failed for peerId=$peerId requesterId=${requesterId.value}: " +
                    e.javaClass.simpleName,
            )
            null
        }
    }

    private fun groupDossier(
        groupFromId: FromId,
        chatMessageCount: Long?,
        peerId: Long,
        requesterId: FromId?,
    ): ProfileDossier? {
        val profile = findStoredGroupProfilesByIdsPort.execute(
            FindStoredGroupProfilesByIdsPort.Request(listOf(groupFromId)),
        ).profiles.firstOrNull()
            ?: getGroupProfilesBatchPort.execute(
                GetGroupProfilesBatchPort.Request(listOf(groupFromId)),
            ).profiles.firstOrNull()
            ?: return null
        return ProfileDossier.GroupDossier(
            profile = profile,
            chatMessageCount = chatMessageCount,
            chatTopics = chatTopics(peerId),
            requesterProfile = requesterProfile(requesterId, peerId),
            requesterKnowledge = requesterId?.let { knowledge(it, peerId, REQUESTER_ID_LABEL) },
        )
    }

    private fun participants(authors: List<ChatAuthor>): List<VkUserProfile> {
        val ids = authors.map { it.fromId }.filter { it.value > 0 && it.value != botId }
        return storedUserProfiles(ids)
    }

    private fun clarification(targets: List<ProfileTarget>): String {
        val shown = targets.take(MAX_CLARIFICATION_CANDIDATES)
        val userProfiles = storedUserProfiles(shown.filterIsInstance<ProfileTarget.User>().map { it.fromId })
        val groupProfiles = storedGroupProfiles(shown.filterIsInstance<ProfileTarget.Group>().map { it.fromId })
        val candidates = shown.mapIndexed { index, target ->
            "${index + 1}) ${candidateText(target, userProfiles, groupProfiles)}"
        }
        return listOf(CLARIFICATION_HEADER, candidates.joinToString(CANDIDATE_SEPARATOR), CLARIFICATION_FOOTER)
            .joinToString(LINE_SEPARATOR)
    }

    private fun candidateText(
        target: ProfileTarget,
        userProfiles: List<VkUserProfile>,
        groupProfiles: List<VkGroupProfile>,
    ): String = when (target) {
        is ProfileTarget.User -> userProfiles.firstOrNull { it.id == target.fromId }
            ?.let { userCandidate(it) }
            ?: idHint(target.fromId)
        is ProfileTarget.Group -> groupProfiles.firstOrNull { it.id == target.fromId }
            ?.let { groupCandidate(it, target.fromId) }
            ?: idHint(target.fromId)
    }

    private fun userCandidate(profile: VkUserProfile): String {
        val name = listOfNotNull(profile.firstName, profile.lastName)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(SPACE)
        return candidateLine(name, profile.screenName, profile.id)
    }

    private fun groupCandidate(profile: VkGroupProfile, groupFromId: FromId): String {
        val name = profile.name?.trim().orEmpty()
        return candidateLine(name, profile.screenName, groupFromId)
    }

    private fun candidateLine(name: String, screenName: String?, fromId: FromId): String {
        if (name.isEmpty()) {
            return idHint(fromId)
        }
        val hints = listOfNotNull(
            screenName?.trim()?.takeIf(String::isNotEmpty)?.let { "$SCREEN_NAME_PREFIX$it" },
            idHint(fromId),
        )
        return "$name (${hints.joinToString(", ")})"
    }

    private fun idHint(fromId: FromId): String = ID_HINT_TEMPLATE.format(fromId.value)

    private fun storedUserProfiles(ids: List<FromId>): List<VkUserProfile> = ids
        .takeIf { it.isNotEmpty() }
        ?.let { findStoredUserProfilesByIdsPort.execute(FindStoredUserProfilesByIdsPort.Request(it)).profiles }
        .orEmpty()

    private fun storedGroupProfiles(ids: List<FromId>): List<VkGroupProfile> = ids
        .takeIf { it.isNotEmpty() }
        ?.let { findStoredGroupProfilesByIdsPort.execute(FindStoredGroupProfilesByIdsPort.Request(it)).profiles }
        .orEmpty()

    private fun notFound(phrase: String): String = NOT_FOUND_TEMPLATE.replace(PHRASE_PLACEHOLDER, phrase)

    private fun publish(text: String, message: Message) {
        publishEventCommand.execute(
            PublishEventRequest(
                event = Event.ANSWER_MESSAGE,
                payload = Payload(
                    ResponseMessage(
                        messageText = MessageText.of(text),
                        responseTo = message,
                    ),
                ),
            ),
        )
    }

    private fun logGenerationFailure(peerId: Long, exception: ProfileGenerationException) {
        logger.log(
            System.Logger.Level.ERROR,
            "Profile answer generation failed for peerId=$peerId: ${exception.javaClass.simpleName}",
        )
    }
}
