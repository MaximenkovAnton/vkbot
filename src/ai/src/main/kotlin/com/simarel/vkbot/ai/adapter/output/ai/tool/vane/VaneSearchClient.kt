package com.simarel.vkbot.ai.adapter.output.ai.tool.vane

import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "vane")
@Path("/api")
interface VaneSearchClient {

    @POST
    @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun search(request: VaneSearchRequest): VaneSearchResponse

    @GET
    @Path("/providers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getProviders(): VaneProvidersResponse
}

data class VaneSearchRequest(
    val chatModel: VaneModelConfig,
    val embeddingModel: VaneModelConfig,
    val optimizationMode: String = "speed",
    val sources: List<String> = listOf("web"),
    val query: String,
    val history: List<List<String>> = emptyList(),
    val stream: Boolean = false,
)

data class VaneModelConfig(
    val providerId: String,
    val key: String,
)

data class VaneSearchResponse(
    val message: String,
    val sources: List<VaneSource>?,
)

data class VaneSource(
    val content: String,
    val metadata: VaneSourceMetadata,
)

data class VaneSourceMetadata(
    val title: String,
    val url: String,
)

data class VaneProvidersResponse(
    val providers: List<VaneProvider>,
)

data class VaneProvider(
    val id: String,
    val name: String,
    val chatModels: List<VaneModel>?,
    val embeddingModels: List<VaneModel>?,
)

data class VaneModel(
    val name: String,
    val key: String,
)
