package com.simarel.vkbot.ai.port.output.ocr

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface ImageRecognitionOutputPort :
    OutputPort<
            ImageRecognitionRequest,
            ImageRecognitionResponse,
            >

data class ImageRecognitionRequest(
    val imageUrls: List<String>,
) : OutputPortRequest

data class ImageRecognitionResponse(
    val results: List<ImageRecognitionResult>,
) : OutputPortResponse

data class ImageRecognitionResult(
    val imageUrl: String,
    val shortDescription: String,
    val fullDescription: String,
)
