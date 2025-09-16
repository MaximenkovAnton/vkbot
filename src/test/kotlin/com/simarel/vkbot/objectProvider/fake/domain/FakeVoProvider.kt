package com.simarel.vkbot.objectProvider.fake.domain

import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.Date
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.GroupId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import java.time.OffsetDateTime
import kotlin.random.Random

object FakeVoProvider {
    fun createDate(value: OffsetDateTime? = null) = Date(value ?: OffsetDateTime.now())
    fun createFromId(value: Long? = null) = FromId.Companion.of(value ?: Random.nextLong())
    fun createGroupId(value: Long? = null) = GroupId.Companion.of(value ?: Random.nextLong())
    fun createPeerId(value: Long? = null) = PeerId.Companion.of(value ?: Random.nextLong())
    fun createConversationMessageId(value: Long? = null) = ConversationMessageId.Companion.of(value ?: Random.nextLong())
    fun createMessageText(value: String? = null) = MessageText.Companion.of(value ?: "Тестовое сообщение")

}