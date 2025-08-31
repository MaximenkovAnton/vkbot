package com.simarel.port

fun interface Port<REQ: Request, RESP: Response> {
    fun execute(request: REQ): RESP
}

interface Request

interface Response