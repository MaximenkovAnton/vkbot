package com.simarel.vkbot.ai.port.output.knowledge

import com.simarel.vkbot.ai.domain.knowledge.KnowledgeFactDraft
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

fun interface KnowledgeExtractionPort : OutputPort<KnowledgeExtractionRequest, KnowledgeExtractionResponse>

data class KnowledgeExtractionRequest(
    val messages: List<String>,
) : OutputPortRequest

data class KnowledgeExtractionResponse(
    val facts: List<KnowledgeFactDraft>? = null,
) : OutputPortResponse
