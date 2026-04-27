package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.port.output.persistence.FilterExistingUserIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.SaveUserProfilePort
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.UserProfileRepositoryPort
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Typed
import jakarta.transaction.Transactional

@Typed(UserProfileRepositoryPort::class)
@ApplicationScoped
open class UserProfileRepositoryAdapter(
    private val savePort: SaveUserProfilePort,
    private val findByIdsPort: FindUserProfilesByIdsPort,
    private val filterExistingPort: FilterExistingUserIdsPort,
) : UserProfileRepositoryPort {

    @Transactional
    override fun save(profile: VkUserProfile) = savePort.save(profile)

    override fun findByIds(fromIds: Collection<FromId>): List<VkUserProfile> =
        findByIdsPort.findByIds(fromIds)

    override fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId> =
        filterExistingPort.filterExistingIds(fromIds)
}
