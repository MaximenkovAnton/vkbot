package com.simarel.vkbot.persistence.command.fetchuserprofile

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.Message

interface FetchUserProfileCommand : Command<FetchUserProfileRequest, FetchUserProfileResponse>

class FetchUserProfileRequest(val message: Message) : CommandRequest

class FetchUserProfileResponse(val success: Boolean) : CommandResponse
