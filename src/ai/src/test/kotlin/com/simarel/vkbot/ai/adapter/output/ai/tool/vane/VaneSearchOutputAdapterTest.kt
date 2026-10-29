package com.simarel.vkbot.ai.adapter.output.ai.tool.vane

import com.simarel.vkbot.ai.testfixtures.FakeVaneProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Stream

class VaneSearchOutputAdapterTest {

    @Test
    fun `execute picks the first provider that has both a chat and an embedding model`() {
        val chatOnly = FakeVaneProvider.createVaneProvider(id = "chat-only", embeddingModels = null)
        val embeddingOnly = FakeVaneProvider.createVaneProvider(id = "embedding-only", chatModels = emptyList())
        val complete = FakeVaneProvider.createVaneProvider(id = "complete")
        val later = FakeVaneProvider.createVaneProvider(id = "later")
        val client = RecordingVaneSearchClient(
            providersResponse = FakeVaneProvider.createVaneProvidersResponse(
                providers = listOf(chatOnly, embeddingOnly, complete, later),
            ),
        )

        VaneSearchOutputAdapter(client).execute(FakeVaneProvider.createVanePortSearchRequest())

        val actual = client.requests.single()
        assertEquals("complete", actual.chatModel.providerId)
        assertEquals("complete", actual.embeddingModel.providerId)
    }

    @Test
    fun `execute sends the first model keys of the selected provider with the search request`() {
        val provider = FakeVaneProvider.createVaneProvider(
            id = "selected",
            chatModels = listOf(
                FakeVaneProvider.createVaneModel(name = "chat-a", key = "chat-first"),
                FakeVaneProvider.createVaneModel(name = "chat-b", key = "chat-second"),
            ),
            embeddingModels = listOf(
                FakeVaneProvider.createVaneModel(name = "embedding-a", key = "embedding-first"),
                FakeVaneProvider.createVaneModel(name = "embedding-b", key = "embedding-second"),
            ),
        )
        val client = RecordingVaneSearchClient(
            providersResponse = FakeVaneProvider.createVaneProvidersResponse(providers = listOf(provider)),
        )
        val request = FakeVaneProvider.createVanePortSearchRequest(
            query = "vane query",
            sources = listOf("academic"),
            optimizationMode = "quality",
        )

        VaneSearchOutputAdapter(client).execute(request)

        assertEquals(
            FakeVaneProvider.createVaneClientSearchRequest(
                chatModel = FakeVaneProvider.createVaneModelConfig(providerId = "selected", key = "chat-first"),
                embeddingModel = FakeVaneProvider.createVaneModelConfig(
                    providerId = "selected",
                    key = "embedding-first",
                ),
                optimizationMode = "quality",
                sources = listOf("academic"),
                query = "vane query",
            ),
            client.requests.single(),
        )
    }

    @Test
    fun `execute maps the message and each source title and url to the port response`() {
        val client = RecordingVaneSearchClient(
            searchResponse = FakeVaneProvider.createVaneClientSearchResponse(
                message = "vane message",
                sources = listOf(
                    FakeVaneProvider.createVaneClientSource(
                        content = "first content",
                        metadata = FakeVaneProvider.createVaneClientSourceMetadata(
                            title = "First source",
                            url = "https://example.com/first",
                        ),
                    ),
                    FakeVaneProvider.createVaneClientSource(
                        content = "second content",
                        metadata = FakeVaneProvider.createVaneClientSourceMetadata(
                            title = "Second source",
                            url = "https://example.com/second",
                        ),
                    ),
                ),
            ),
        )

        val response = VaneSearchOutputAdapter(client).execute(FakeVaneProvider.createVanePortSearchRequest())

        assertEquals(
            FakeVaneProvider.createVanePortSearchResponse(
                message = "vane message",
                sources = listOf(
                    FakeVaneProvider.createVanePortSource(
                        content = "first content",
                        title = "First source",
                        url = "https://example.com/first",
                    ),
                    FakeVaneProvider.createVanePortSource(
                        content = "second content",
                        title = "Second source",
                        url = "https://example.com/second",
                    ),
                ),
            ),
            response,
        )
    }

    @Test
    fun `execute maps missing sources to an empty source list`() {
        val client = RecordingVaneSearchClient(
            searchResponse = FakeVaneProvider.createVaneClientSearchResponse(message = "no sources", sources = null),
        )

        val response = VaneSearchOutputAdapter(client).execute(FakeVaneProvider.createVanePortSearchRequest())

        assertEquals(
            FakeVaneProvider.createVanePortSearchResponse(message = "no sources", sources = emptyList()),
            response,
        )
    }

    @ParameterizedTest
    @MethodSource("unavailableProviderLists")
    fun `execute throws when no provider has both a chat and an embedding model`(providers: List<VaneProvider>) {
        val client = RecordingVaneSearchClient(
            providersResponse = FakeVaneProvider.createVaneProvidersResponse(providers = providers),
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            VaneSearchOutputAdapter(client).execute(FakeVaneProvider.createVanePortSearchRequest())
        }

        assertEquals("No providers configured in Vane", thrown.message)
        assertTrue(client.requests.isEmpty())
    }

    private class RecordingVaneSearchClient(
        private val providersResponse: VaneProvidersResponse = FakeVaneProvider.createVaneProvidersResponse(),
        private val searchResponse: VaneSearchResponse = FakeVaneProvider.createVaneClientSearchResponse(),
    ) : VaneSearchClient {
        val requests = ConcurrentLinkedQueue<VaneSearchRequest>()

        override fun search(request: VaneSearchRequest): VaneSearchResponse {
            requests.add(request)
            return searchResponse
        }

        override fun getProviders(): VaneProvidersResponse = providersResponse
    }

    companion object {
        @JvmStatic
        fun unavailableProviderLists(): Stream<Arguments> = Stream.of(
            Arguments.of(emptyList<VaneProvider>()),
            Arguments.of(listOf(FakeVaneProvider.createVaneProvider(chatModels = null))),
            Arguments.of(listOf(FakeVaneProvider.createVaneProvider(embeddingModels = null))),
            Arguments.of(listOf(FakeVaneProvider.createVaneProvider(embeddingModels = emptyList()))),
        )
    }
}
