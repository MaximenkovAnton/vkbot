package com.simarel.vkbot.receiver.adapter.input.dto

import jakarta.json.JsonObject

data class WallAttachmentDto(
    val id: Long,
    val ownerId: Long,
    val text: String,
    val fromId: Long,
    val fromName: String?,
) {
    companion object {
        fun fromJson(jsonObject: JsonObject): WallAttachmentDto? {
            val wall = jsonObject.getJsonObject("wall") ?: return null

            val text = wall.getString("text", "")

            val from = wall.getJsonObject("from")
            val fromId = from?.getJsonNumber("id")?.longValue()
                ?: wall.getJsonNumber("from_id")?.longValue()
                ?: 0L

            return WallAttachmentDto(
                id = wall.getJsonNumber("id")?.longValue() ?: 0L,
                ownerId = wall.getJsonNumber("owner_id")?.longValue() ?: 0L,
                text = text,
                fromId = fromId,
                fromName = from?.getString("name"),
            )
        }
    }
}
