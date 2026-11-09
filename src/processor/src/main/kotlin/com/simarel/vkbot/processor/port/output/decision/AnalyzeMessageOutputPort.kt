package com.simarel.vkbot.processor.port.output.decision

import com.simarel.vkbot.processor.domain.decision.DecisionAnalysis
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface AnalyzeMessageOutputPort : OutputPort<AnalyzeMessageRequest, AnalyzeMessageResponse>

data class AnalyzeMessageRequest(val messageText: MessageText) : OutputPortRequest

data class AnalyzeMessageResponse(val analysis: DecisionAnalysis) : OutputPortResponse
