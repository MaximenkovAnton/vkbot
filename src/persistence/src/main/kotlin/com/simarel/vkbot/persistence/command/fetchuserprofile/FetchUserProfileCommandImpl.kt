package com.simarel.vkbot.persistence.command.fetchuserprofile

import com.simarel.vkbot.persistence.port.output.persistence.FilterExistingGroupIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FilterExistingUserIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.SaveGroupProfilePort
import com.simarel.vkbot.persistence.port.output.persistence.SaveUserProfilePort
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.vkFacade.port.output.vk.GetProfileOutputPort
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class FetchUserProfileCommandImpl(
    private val userProfileRepositoryPort: FilterExistingUserIdsPort,
    private val groupProfileRepositoryPort: FilterExistingGroupIdsPort,
    private val saveGroupProfilePort: SaveGroupProfilePort,
    private val saveUserProfilePort: SaveUserProfilePort,
    private val getProfileOutputPort: GetProfileOutputPort,
) : FetchUserProfileCommand {

    @Transactional
    override fun execute(request: FetchUserProfileRequest): FetchUserProfileResponse {
        val allFromIds = collectAllFromIds(request.message)

        val (potentialUserIds, potentialGroupIds) = allFromIds.partition { it.isHuman() || it.isGroupChat() }
            .let { (users, groups) ->
                users.filter { it.isHuman() } to groups.filter { it.isGroup() }
            }

        val existingUserIds = userProfileRepositoryPort.filterExistingIds(potentialUserIds)
        val missingUserIds = potentialUserIds - existingUserIds

        val existingGroupIds = groupProfileRepositoryPort.filterExistingIds(potentialGroupIds)
        val missingGroupIds = potentialGroupIds - existingGroupIds

        if (missingUserIds.isEmpty() && missingGroupIds.isEmpty()) {
            return FetchUserProfileResponse(success = true)
        }

        if (missingUserIds.isNotEmpty()) {
            fetchAndSaveUserProfiles(missingUserIds)
        }
        if (missingGroupIds.isNotEmpty()) {
            fetchAndSaveGroupProfiles(missingGroupIds)
        }

        return FetchUserProfileResponse(success = true)
    }

    private fun collectAllFromIds(message: Message): Set<FromId> {
        return generateSequence(listOf(message)) { messages ->
            messages.flatMap { it.forwardedMessages }.takeIf { it.isNotEmpty() }
        }.flatMap { it.asSequence().map(Message::fromId) }.toSet()
    }

    private fun fetchAndSaveUserProfiles(userIds: List<FromId>) {
        try {
            val profiles = getProfileOutputPort.getUserProfilesBatch(userIds)
            profiles.forEach { profile ->
                saveUserProfilePort.save(profile)
                Log.debug("Saved user profile for id: ${profile.id.value}")
            }
        } catch (e: Exception) {
            Log.warn("Failed to fetch/save user profiles for ids: ${userIds.map { it.value }}, error: ${e.message}")
        }
    }

    private fun fetchAndSaveGroupProfiles(groupIds: List<FromId>) {
        try {
            val profiles = getProfileOutputPort.getGroupProfilesBatch(groupIds)
            profiles.forEach { profile ->
                saveGroupProfilePort.save(profile)
                Log.debug("Saved group profile for id: ${profile.id.value}")
            }
        } catch (e: Exception) {
            Log.warn("Failed to fetch/save group profiles for ids: ${groupIds.map { it.value }}, error: ${e.message}")
        }
    }
}
