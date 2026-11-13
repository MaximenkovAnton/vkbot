package com.simarel.vkbot.share.port.output.ai

import com.simarel.vkbot.share.domain.model.greeting.GreetingPerson
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.domain.vo.FromId
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
        val occasion: String,
        val people: List<GreetingPerson> = emptyList(),
        val chatFacts: String? = null,
        val personalKnowledge: Map<FromId, UserKnowledge> = emptyMap(),
    ) : OutputPortRequest
    data class Response(val text: MessageText) : OutputPortResponse
}
