package com.simarel.vkbot.ai.usecase.news

import com.simarel.vkbot.ai.command.news.ProcessNewsDigestCommand
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPort
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPortRequest
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessNewsDigestUsecase(
    private val processNewsDigestCommand: ProcessNewsDigestCommand,
) : ProcessNewsDigestInputPort {

    override fun execute(request: ProcessNewsDigestInputPortRequest): ProcessNewsDigestInputPortResponse {
        processNewsDigestCommand.execute(
            ProcessNewsDigestCommand.ProcessNewsDigestRequest(request.message),
        )
        return ProcessNewsDigestInputPortResponse
    }
}
