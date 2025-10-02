package com.simarel.vkbot.share.domain.vo

@JvmInline
value class GroupId(val value: Long) {
    companion object {
        fun of(value: Long) = GroupId(value)
    }
    override fun toString(): String = value.toString()
}
