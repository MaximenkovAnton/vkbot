package com.simarel.vkbot.ai.testfixtures

import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneModel
import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneModelConfig
import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneProvider
import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneProvidersResponse
import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneSourceMetadata
import com.simarel.vkbot.ai.command.vane.VaneSearchCommandRequest
import com.simarel.vkbot.ai.command.vane.VaneSearchCommandResponse
import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneSearchRequest as VaneClientSearchRequest
import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneSearchResponse as VaneClientSearchResponse
import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneSource as VaneClientSource
import com.simarel.vkbot.ai.port.output.vane.VaneSearchRequest as VanePortSearchRequest
import com.simarel.vkbot.ai.port.output.vane.VaneSearchResponse as VanePortSearchResponse
import com.simarel.vkbot.ai.port.output.vane.VaneSource as VanePortSource

object FakeVaneProvider {

    fun createVanePortSearchRequest(
        query: String = "kotlin coroutines",
        sources: List<String> = listOf("web"),
        optimizationMode: String = "balanced",
    ) = VanePortSearchRequest(
        query = query,
        sources = sources,
        optimizationMode = optimizationMode,
    )

    fun createVanePortSearchResponse(
        message: String = "Vane answer",
        sources: List<VanePortSource> = listOf(createVanePortSource()),
    ) = VanePortSearchResponse(
        message = message,
        sources = sources,
    )

    fun createVanePortSource(
        content: String = "source content",
        title: String = "Source title",
        url: String = "https://example.com/source",
    ) = VanePortSource(
        content = content,
        title = title,
        url = url,
    )

    fun createVaneClientSearchRequest(
        chatModel: VaneModelConfig = createVaneModelConfig(),
        embeddingModel: VaneModelConfig = createVaneModelConfig(key = "embedding-key"),
        optimizationMode: String = "speed",
        sources: List<String> = listOf("web"),
        query: String = "kotlin coroutines",
        history: List<List<String>> = emptyList(),
        stream: Boolean = false,
    ) = VaneClientSearchRequest(
        chatModel = chatModel,
        embeddingModel = embeddingModel,
        optimizationMode = optimizationMode,
        sources = sources,
        query = query,
        history = history,
        stream = stream,
    )

    fun createVaneModelConfig(
        providerId: String = "vane-provider",
        key: String = "chat-key",
    ) = VaneModelConfig(
        providerId = providerId,
        key = key,
    )

    fun createVaneClientSearchResponse(
        message: String = "Vane answer",
        sources: List<VaneClientSource>? = listOf(createVaneClientSource()),
    ) = VaneClientSearchResponse(
        message = message,
        sources = sources,
    )

    fun createVaneClientSource(
        content: String = "source content",
        metadata: VaneSourceMetadata = createVaneClientSourceMetadata(),
    ) = VaneClientSource(
        content = content,
        metadata = metadata,
    )

    fun createVaneClientSourceMetadata(
        title: String = "Source title",
        url: String = "https://example.com/source",
    ) = VaneSourceMetadata(
        title = title,
        url = url,
    )

    fun createVaneProvidersResponse(
        providers: List<VaneProvider> = listOf(createVaneProvider()),
    ) = VaneProvidersResponse(providers)

    fun createVaneProvider(
        id: String = "vane-provider",
        name: String = "Vane Provider",
        chatModels: List<VaneModel>? = listOf(createVaneModel()),
        embeddingModels: List<VaneModel>? = listOf(createVaneModel(name = "embedding-model", key = "embedding-key")),
    ) = VaneProvider(
        id = id,
        name = name,
        chatModels = chatModels,
        embeddingModels = embeddingModels,
    )

    fun createVaneModel(
        name: String = "chat-model",
        key: String = "chat-key",
    ) = VaneModel(
        name = name,
        key = key,
    )

    fun createVaneWebSearchCommandRequest(
        query: String = "kotlin coroutines",
    ) = VaneSearchCommandRequest.WebSearch(query)

    fun createVaneAcademicSearchCommandRequest(
        query: String = "kotlin coroutines",
    ) = VaneSearchCommandRequest.AcademicSearch(query)

    fun createVaneDiscussionSearchCommandRequest(
        query: String = "kotlin coroutines",
    ) = VaneSearchCommandRequest.DiscussionSearch(query)

    fun createVaneSearchCommandResponse(
        result: String = "Vane answer",
    ) = VaneSearchCommandResponse(result)
}
