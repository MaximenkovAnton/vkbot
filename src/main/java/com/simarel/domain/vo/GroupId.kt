package com.simarel.domain.vo

@JvmInline
value class GroupId(val value: Long) {
    companion object {
        fun of(value: Long) = GroupId(value)
    }
    override fun toString(): String {
        return value.toString()
    }
}