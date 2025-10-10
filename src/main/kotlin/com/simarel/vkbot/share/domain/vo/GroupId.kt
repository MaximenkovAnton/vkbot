package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.receiver.domain.exception.ValidationException

@JvmInline
value class GroupId(val value: Long) {
    companion object {
        fun of(value: Long?): GroupId {
            if (value == null) throw ValidationGroupIdNotNullException()
            return GroupId(value)
        }
    }
    override fun toString(): String = value.toString()
}

class ValidationGroupIdNotNullException : ValidationException(message = "Group Id can't be null")
