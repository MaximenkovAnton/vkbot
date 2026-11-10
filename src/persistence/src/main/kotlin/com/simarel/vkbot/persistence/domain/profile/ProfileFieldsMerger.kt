package com.simarel.vkbot.persistence.domain.profile

import com.simarel.vkbot.share.domain.model.VkUserProfile
import java.math.BigDecimal

object ProfileFieldsMerger {

    fun merge(storedDetails: Map<String, Any?>, fetchedDetails: Map<String, Any?>): Map<String, Any?> {
        val merged = LinkedHashMap<String, Any?>(storedDetails)
        fetchedDetails.forEach { (name, value) ->
            if (value != null) {
                merged[name] = value
            }
        }
        return merged
    }

    fun diff(previous: VkUserProfile, current: VkUserProfile): Map<String, Map<String, Any?>> {
        val changes = LinkedHashMap(typedDiff(previous, current))
        if (previous.details.isNotEmpty()) {
            changes.putAll(detailsDiff(previous.details, current.details))
        }
        return changes
    }

    private fun detailsDiff(
        previous: Map<String, Any?>,
        current: Map<String, Any?>,
    ): Map<String, Map<String, Any?>> {
        val names = LinkedHashSet<String>()
        names.addAll(previous.keys)
        names.addAll(current.keys)
        val changes = LinkedHashMap<String, Map<String, Any?>>()
        names.forEach { name ->
            addChange(changes, name, previous[name], current[name])
        }
        return changes
    }

    private fun typedDiff(previous: VkUserProfile, current: VkUserProfile): Map<String, Map<String, Any?>> {
        val changes = LinkedHashMap<String, Map<String, Any?>>()
        addChange(changes, FIRST_NAME, previous.firstName, current.firstName)
        addChange(changes, LAST_NAME, previous.lastName, current.lastName)
        addChange(changes, SCREEN_NAME, previous.screenName, current.screenName)
        addChange(changes, BIRTH_DATE, previous.birthDate, current.birthDate)
        addChange(changes, CITY, previous.city, current.city)
        return changes
    }

    private fun addChange(
        changes: MutableMap<String, Map<String, Any?>>,
        name: String,
        old: Any?,
        new: Any?,
    ) {
        if (!equivalent(old, new)) {
            changes[name] = linkedMapOf("old" to old, "new" to new)
        }
    }

    private fun equivalent(first: Any?, second: Any?): Boolean = normalize(first) == normalize(second)

    private fun normalize(value: Any?): Any? = when (value) {
        is Number -> BigDecimal(value.toString()).stripTrailingZeros()
        is Map<*, *> -> value.entries.associate { entry -> entry.key to normalize(entry.value) }
        is Collection<*> -> value.map(::normalize)
        else -> value
    }

    private const val FIRST_NAME = "first_name"
    private const val LAST_NAME = "last_name"
    private const val SCREEN_NAME = "screen_name"
    private const val BIRTH_DATE = "birth_date"
    private const val CITY = "city"
}
