package com.simarel.vkbot.ai.command.ocr

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse

interface RecognizeImagesCommand : Command<RecognizeImagesRequest, RecognizeImagesResponse>

data class RecognizeImagesRequest(
    val imageUrls: List<String>,
) : CommandRequest

data class RecognizeImagesResponse(
    val results: List<ImageRecognitionResult>,
) : CommandResponse

data class ImageRecognitionResult(
    val imageUrl: String,
    val shortDescription: String,
    val fullDescription: String,
)
