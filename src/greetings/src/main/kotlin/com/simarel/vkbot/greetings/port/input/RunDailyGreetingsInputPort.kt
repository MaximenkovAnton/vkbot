package com.simarel.vkbot.greetings.port.input

import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse
import java.time.LocalDate

interface RunDailyGreetingsInputPort :
    InputPort<
        RunDailyGreetingsInputPort.Request,
        RunDailyGreetingsInputPort.Response,
        > {
    data class Request(val date: LocalDate) : InputPortRequest
    data class Response(val planned: Int, val sent: Int, val failed: Int) : InputPortResponse
}
