package com.simarel.vkbot.ai.adapter.output.ocr

import dev.langchain4j.data.image.Image
import io.quarkiverse.langchain4j.RegisterAiService
import jakarta.enterprise.context.ApplicationScoped

@RegisterAiService
@ApplicationScoped
interface OcrAiService {

    @dev.langchain4j.service.SystemMessage("{config:aiOcrSystemPrompt}")
    @dev.langchain4j.service.UserMessage(
        """
        Проанализируй все изображения и для каждого верни результат в следующем формате JSON:
        {
          "results": [
            {
              "imageIndex": 0,
              "shortDescription": "краткое описание в одном предложении",
              "fullDescription": "подробное описание всех видимых деталей, объектов, текста, людей, действий"
            }
          ]
        }

        Поля:
        - imageIndex: порядковый номер изображения, начиная с 0
        - shortDescription: сжатое описание основного содержимого
        - fullDescription: максимально детальное описание всего, что видно на изображении

        Отвечай ТОЛЬКО валидным JSON без дополнительного текста.
        """
    )
    fun recognize(images: List<Image>): OcrResults
}

data class OcrResults(
    val results: List<OcrResult>
)

data class OcrResult(
    val imageIndex: Int,
    val shortDescription: String,
    val fullDescription: String,
)
