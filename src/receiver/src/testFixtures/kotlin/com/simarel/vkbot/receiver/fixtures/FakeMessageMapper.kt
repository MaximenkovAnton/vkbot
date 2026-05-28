package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.adapter.input.vk.mapper.MessageMapper
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import jakarta.json.JsonObject

class FakeMessageMapper : MessageMapper() {
    var toDomainCalls = mutableListOf<JsonObject>()

    override fun toDomain(body: JsonObject): Message {
        toDomainCalls.add(body)
        return FakeVoProvider.createMessage()
    }
}
