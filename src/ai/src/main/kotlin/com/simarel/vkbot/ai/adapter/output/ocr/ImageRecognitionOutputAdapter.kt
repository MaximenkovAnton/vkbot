package com.simarel.vkbot.ai.adapter.output.ocr

import com.simarel.vkbot.ai.port.output.ocr.ImageRecognitionOutputPort
import com.simarel.vkbot.ai.port.output.ocr.ImageRecognitionRequest
import com.simarel.vkbot.ai.port.output.ocr.ImageRecognitionResponse
import com.simarel.vkbot.ai.port.output.ocr.ImageRecognitionResult
import dev.langchain4j.data.image.Image
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ImageRecognitionOutputAdapter(
    private val ocrAiService: OcrAiService,
) : ImageRecognitionOutputPort {

    override fun execute(request: ImageRecognitionRequest): ImageRecognitionResponse {
        if (request.imageUrls.isEmpty()) {
            return ImageRecognitionResponse(emptyList())
        }

        val images = request.imageUrls.map { url ->
            Image.builder().url(url).build()
        }

        val ocrResults = ocrAiService.recognize(images)

        return ImageRecognitionResponse(
            results = ocrResults.results.map { result ->
                ImageRecognitionResult(
                    imageUrl = request.imageUrls[result.imageIndex],
                    shortDescription = result.shortDescription,
                    fullDescription = result.fullDescription,
                )
            }
        )
    }
}
