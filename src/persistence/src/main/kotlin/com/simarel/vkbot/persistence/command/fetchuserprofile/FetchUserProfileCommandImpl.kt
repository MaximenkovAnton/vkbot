package com.simarel.vkbot.persistence.command.fetchuserprofile

import com.simarel.vkbot.persistence.domain.profile.ProfileFieldsMerger
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.SaveGroupProfilePort
import com.simarel.vkbot.persistence.port.output.persistence.SaveUserProfilePort
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime

@ApplicationScoped
open class FetchUserProfileCommandImpl(
    private val findUserProfilesByIdsPort: FindUserProfilesByIdsPort,
    private val findGroupProfilesByIdsPort: FindGroupProfilesByIdsPort,
    private val saveGroupProfilePort: SaveGroupProfilePort,
    private val saveUserProfilePort: SaveUserProfilePort,
    private val getUserProfilesBatchPort: GetUserProfilesBatchPort,
    private val getGroupProfilesBatchPort: GetGroupProfilesBatchPort,
) : FetchUserProfileCommand {

    private val logger = System.getLogger(FetchUserProfileCommandImpl::class.java.name)

    @Transactional
    override fun execute(request: FetchUserProfileRequest): FetchUserProfileResponse {
        val allFromIds = collectAllFromIds(request.message)
        val userIds = allFromIds.filter { it.isHuman() }
        val groupIds = allFromIds.filter { it.isGroup() }

        val existingUsers = findUserProfilesByIdsPort.execute(
            FindUserProfilesByIdsPort.FindUserProfilesByIdsRequest(userIds),
        ).profiles.associateBy(VkUserProfile::id)
        val existingGroups = findGroupProfilesByIdsPort.execute(
            FindGroupProfilesByIdsPort.FindGroupProfilesByIdsRequest(groupIds),
        ).profiles.associateBy(VkGroupProfile::id)

        val actualizedAt = OffsetDateTime.now()
        val staleBefore = actualizedAt.minusMonths(ACTUALIZATION_PERIOD_MONTHS)
        val usersToActualize = userIds.filter { shouldActualize(existingUsers[it]?.lastUpdated, staleBefore) }
        val groupsToActualize = groupIds.filter { shouldActualize(existingGroups[it]?.lastUpdated, staleBefore) }

        if (usersToActualize.isEmpty() && groupsToActualize.isEmpty()) {
            return FetchUserProfileResponse(success = true)
        }

        if (usersToActualize.isNotEmpty()) {
            actualizeUserProfiles(usersToActualize, existingUsers, actualizedAt)
        }
        if (groupsToActualize.isNotEmpty()) {
            actualizeGroupProfiles(groupsToActualize, existingGroups, actualizedAt)
        }

        return FetchUserProfileResponse(success = true)
    }

    private fun collectAllFromIds(message: Message): Set<FromId> = generateSequence(listOf(message)) { messages ->
        messages.flatMap { it.forwardedMessages }.takeIf { it.isNotEmpty() }
    }.flatMap { it.asSequence().map(Message::fromId) }.toSet()

    private fun shouldActualize(lastUpdated: OffsetDateTime?, staleBefore: OffsetDateTime): Boolean {
        if (lastUpdated == null) {
            return true
        }
        return lastUpdated.isBefore(staleBefore)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun actualizeUserProfiles(
        userIds: List<FromId>,
        existingProfiles: Map<FromId, VkUserProfile>,
        actualizedAt: OffsetDateTime,
    ) {
        try {
            val response = getUserProfilesBatchPort.execute(GetUserProfilesBatchPort.Request(userIds))
            response.profiles.forEach { fetched ->
                val existing = existingProfiles[fetched.id]
                val merged = mergeUserProfile(existing, fetched, actualizedAt)
                val changes = existing?.let { ProfileFieldsMerger.diff(it, merged) }?.takeIf { it.isNotEmpty() }
                saveUserProfilePort.execute(SaveUserProfilePort.SaveUserProfileRequest(merged, changes))
                logger.log(System.Logger.Level.DEBUG, "Actualized user profile for id: ${merged.id.value}")
            }
        } catch (e: Exception) {
            logger.log(
                System.Logger.Level.WARNING,
                "Failed to fetch/save user profiles for ids: ${userIds.map { it.value }}, error: ${e.message}",
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun actualizeGroupProfiles(
        groupIds: List<FromId>,
        existingProfiles: Map<FromId, VkGroupProfile>,
        actualizedAt: OffsetDateTime,
    ) {
        try {
            val response = getGroupProfilesBatchPort.execute(GetGroupProfilesBatchPort.Request(groupIds))
            response.profiles.forEach { fetched ->
                val merged = mergeGroupProfile(existingProfiles[fetched.id], fetched, actualizedAt)
                saveGroupProfilePort.execute(SaveGroupProfilePort.SaveGroupProfileRequest(merged))
                logger.log(System.Logger.Level.DEBUG, "Actualized group profile for id: ${merged.id.value}")
            }
        } catch (e: Exception) {
            logger.log(
                System.Logger.Level.WARNING,
                "Failed to fetch/save group profiles for ids: ${groupIds.map { it.value }}, error: ${e.message}",
            )
        }
    }

    private fun mergeUserProfile(
        existing: VkUserProfile?,
        fetched: VkUserProfile,
        actualizedAt: OffsetDateTime,
    ): VkUserProfile = VkUserProfile(
        id = fetched.id,
        firstName = fetched.firstName ?: existing?.firstName,
        lastName = fetched.lastName ?: existing?.lastName,
        screenName = fetched.screenName ?: existing?.screenName,
        birthDate = fetched.birthDate ?: existing?.birthDate,
        city = fetched.city ?: existing?.city,
        lastUpdated = actualizedAt,
        details = mergeDetails(existing, fetched),
    )

    private fun mergeDetails(existing: VkUserProfile?, fetched: VkUserProfile): Map<String, Any?> {
        if (existing == null) {
            return fetched.details
        }
        return ProfileFieldsMerger.merge(existing.details, fetched.details)
    }

    private fun mergeGroupProfile(
        existing: VkGroupProfile?,
        fetched: VkGroupProfile,
        actualizedAt: OffsetDateTime,
    ): VkGroupProfile = VkGroupProfile(
        id = fetched.id,
        name = fetched.name ?: existing?.name,
        screenName = fetched.screenName ?: existing?.screenName,
        lastUpdated = actualizedAt,
    )

    private companion object {
        const val ACTUALIZATION_PERIOD_MONTHS = 1L
    }
}
