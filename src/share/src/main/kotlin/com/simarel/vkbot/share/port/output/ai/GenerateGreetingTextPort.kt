package com.simarel.vkbot.share.port.output.ai

import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.LocalDate

interface GenerateGreetingTextPort :
    OutputPort<
        GenerateGreetingTextPort.Request,
        GenerateGreetingTextPort.Response,
        > {
    data class Request(
        val date: LocalDate,
        val holidays: List<String>,
        val people: List<String>,
    ) : OutputPortRequest
    data class Response(val text: MessageText) : OutputPortResponse
}
