package com.simarel.vkbot.objectProvider.fake.domain

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.Date
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.GroupId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId

object FakeMessageProvider {
    fun createMessage(
        date: Date = FakeVoProvider.createDate(),
        fromId: FromId = FakeVoProvider.createFromId(),
        groupId: GroupId = FakeVoProvider.createGroupId(),
        peerId: PeerId =  FakeVoProvider.createPeerId(),
        conversationMessageId: ConversationMessageId = FakeVoProvider.createConversationMessageId(),
        messageText: MessageText = FakeVoProvider.createMessageText(),
    ): Message = Message(
        date = date,
        fromId = fromId,
        groupId = groupId,
        peerId = peerId,
        conversationMessageId = conversationMessageId,
        messageText = messageText,
    )
}