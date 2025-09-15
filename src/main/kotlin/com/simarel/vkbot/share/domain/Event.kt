package com.simarel.vkbot.share.domain

enum class Event(val eventName: String) {
    MESSAGE_RECEIVED("message_received"),
    MESSAGE_REQUIRE_ANSWER("message_require_answer")
}