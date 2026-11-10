package com.simarel.vkbot.ai.usecase.profile

import com.simarel.vkbot.ai.command.profile.ProcessProfileCommand
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPort
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPortRequest
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessProfileUsecase(
    private val processProfileCommand: ProcessProfileCommand,
) : ProcessProfileInputPort {

    override fun execute(request: ProcessProfileInputPortRequest): ProcessProfileInputPortResponse {
        processProfileCommand.execute(
            ProcessProfileCommand.ProcessProfileRequest(request.message),
        )
        return ProcessProfileInputPortResponse
    }
}
