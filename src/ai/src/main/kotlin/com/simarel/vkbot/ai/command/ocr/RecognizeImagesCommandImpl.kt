package com.simarel.vkbot.ai.command.ocr

import com.simarel.vkbot.ai.port.output.ocr.ImageRecognitionOutputPort
import com.simarel.vkbot.ai.port.output.ocr.ImageRecognitionRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class RecognizeImagesCommandImpl(
    private val imageRecognitionOutputPort: ImageRecognitionOutputPort,
) : RecognizeImagesCommand {

    override fun execute(request: RecognizeImagesRequest): RecognizeImagesResponse {
        val response = imageRecognitionOutputPort.execute(
            ImageRecognitionRequest(request.imageUrls)
        )

        return RecognizeImagesResponse(
            results = response.results.map {
                ImageRecognitionResult(
                    imageUrl = it.imageUrl,
                    shortDescription = it.shortDescription,
                    fullDescription = it.fullDescription,
                )
            }
        )
    }
}
