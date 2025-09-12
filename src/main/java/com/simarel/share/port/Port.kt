package com.simarel.share.port

fun interface Port<REQ: PortRequest, RESP: PortResponse> {
    fun execute(request: REQ): RESP
}

interface PortRequest

interface PortResponse