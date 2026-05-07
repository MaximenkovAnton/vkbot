package com.simarel.vkbot.ai.adapter.output.ai.tool.vane

import com.simarel.vkbot.ai.port.output.vane.VaneSearchOutputPort
import com.simarel.vkbot.ai.port.output.vane.VaneSearchRequest
import com.simarel.vkbot.ai.port.output.vane.VaneSearchResponse
import com.simarel.vkbot.ai.port.output.vane.VaneSource
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class VaneSearchOutputAdapter(
    @RestClient private val vaneClient: VaneSearchClient,
) : VaneSearchOutputPort {

    override fun execute(request: VaneSearchRequest): VaneSearchResponse {
        val providers = getAvailableProviders()
        val provider = providers.firstOrNull()
            ?: throw IllegalStateException("No providers configured in Vane")

        val vaneRequest = VaneSearchRequest(
            chatModel = VaneModelConfig(providerId = provider.id, key = provider.chatModels.first().key),
            embeddingModel = VaneModelConfig(providerId = provider.id, key = provider.embeddingModels.first().key),
            optimizationMode = request.optimizationMode,
            sources = request.sources,
            query = request.query,
        )

        val response = vaneClient.search(vaneRequest)

        return VaneSearchResponse(
            message = response.message,
            sources = response.sources?.map {
                VaneSource(
                    content = it.content,
                    title = it.metadata.title,
                    url = it.metadata.url,
                )
            } ?: emptyList(),
        )
    }

    private fun getAvailableProviders(): List<ProviderInfo> {
        val response = vaneClient.getProviders()

        return response.providers.map { provider ->
            ProviderInfo(
                id = provider.id,
                name = provider.name,
                chatModels = provider.chatModels?.map {
                    ModelInfo(name = it.name, key = it.key)
                } ?: emptyList(),
                embeddingModels = provider.embeddingModels?.map {
                    ModelInfo(name = it.name, key = it.key)
                } ?: emptyList(),
            )
        }.filter {
            it.chatModels.isNotEmpty() && it.embeddingModels.isNotEmpty()
        }
    }

    private data class ProviderInfo(
        val id: String,
        val name: String,
        val chatModels: List<ModelInfo>,
        val embeddingModels: List<ModelInfo>,
    )

    private data class ModelInfo(
        val name: String,
        val key: String,
    )
}
