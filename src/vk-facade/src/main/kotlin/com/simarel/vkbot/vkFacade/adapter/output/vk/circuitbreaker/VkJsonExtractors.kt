package com.simarel.vkbot.vkFacade.adapter.output.vk.circuitbreaker

import com.fasterxml.jackson.databind.JsonNode

internal fun textOrNull(node: JsonNode?, field: String): String? = node?.get(field)
    ?.takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }

internal fun intOrNull(node: JsonNode?, field: String): Int? = node?.get(field)
    ?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.asInt()

internal fun boolOrNull(node: JsonNode?, field: String): Boolean? {
    val value = node?.get(field) ?: return null
    if (value.isBoolean) {
        return value.asBoolean()
    }
    return when (value.takeIf { it.isIntegralNumber }?.asInt()) {
        0 -> false
        1 -> true
        else -> null
    }
}

internal fun labelOrNull(code: Int?, labels: Map<Int, String>): String? = code?.let(labels::get)

internal fun jsonObjectOrNull(node: JsonNode?, field: String): JsonNode? = node?.get(field)
    ?.takeIf { it.isObject }

internal fun jsonArrayOrNull(node: JsonNode?, field: String): JsonNode? = node?.get(field)
    ?.takeIf { it.isArray }
