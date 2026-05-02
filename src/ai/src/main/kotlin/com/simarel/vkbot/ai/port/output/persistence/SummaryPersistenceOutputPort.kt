package com.simarel.vkbot.ai.port.output.persistence

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import java.util.UUID

interface SummaryPersistenceOutputPort {

    fun createPendingSummary(
        peerId: Long,
        firstMessageId: Long,
        lastMessageId: Long,
    ): UUID

    fun saveCompletedSummary(
        summaryId: UUID,
        shortSummary: String,
        fullSummary: String,
    )

    fun markSummaryAsFailed(summaryId: UUID)

    fun findMessagesBetween(
        peerId: Long,
        firstMessageId: Long,
        lastMessageId: Long,
        limit: Int,
    ): List<StoredMessage>

    fun findMessagesBefore(
        peerId: Long,
        beforeConversationMessageId: Long,
        limit: Int,
    ): List<StoredMessage>

    fun findUserProfilesByIds(ids: List<FromId>): List<VkUserProfile>

    fun findGroupProfilesByIds(ids: List<FromId>): List<VkGroupProfile>

    fun findLastSummary(peerId: Long): Summary?

    fun hasPendingSummary(peerId: Long): Boolean
}
