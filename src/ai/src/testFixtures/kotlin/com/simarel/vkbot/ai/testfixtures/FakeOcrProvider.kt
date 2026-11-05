package com.simarel.vkbot.ai.testfixtures

import com.simarel.vkbot.ai.adapter.output.ai.ocr.OcrResult
import com.simarel.vkbot.ai.adapter.output.ai.ocr.OcrResults
import com.simarel.vkbot.ai.port.output.ocr.ImageRecognitionRequest

object FakeOcrProvider {

    fun createImageRecognitionRequest(
        imageUrls: List<String> = listOf("https://example.com/image.png"),
    ) = ImageRecognitionRequest(imageUrls)

    fun createOcrResults(
        imageIndex: Int = 0,
        shortDescription: String = "short description",
        fullDescription: String = "full description",
    ) = OcrResults(
        listOf(
            OcrResult(
                imageIndex = imageIndex,
                shortDescription = shortDescription,
                fullDescription = fullDescription,
            ),
        ),
    )
}
