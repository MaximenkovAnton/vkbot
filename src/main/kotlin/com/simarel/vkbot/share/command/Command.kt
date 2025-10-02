package com.simarel.vkbot.share.command

fun interface Command<REQ : CommandRequest, RESP : CommandResponse> {
    fun execute(request: REQ): RESP
}

interface CommandRequest
interface CommandResponse
