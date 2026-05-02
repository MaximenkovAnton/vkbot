package com.simarel.vkbot.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.client.WireMock
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager

/**
 * Quarkus test resource that starts WireMock server for integration tests.
 * WireMock is used to mock external HTTP services:
 * - VK API (api.vk.ru/method)
 * - Ollama API (for LangChain4j)
 * - Internal services (vk-facade, persistence)
 */
class WireMockTestResource : QuarkusTestResourceLifecycleManager {

    companion object {
        @JvmStatic
        private var server: WireMockServer? = null

        @JvmStatic
        val wireMockServer: WireMockServer
            get() = server
                ?: throw IllegalStateException("WireMock server not initialized. Ensure @QuarkusTestResource is applied.")

        @JvmStatic
        fun resetToDefaultStubs() {
            server?.resetAll()
            setupVkFacadeStubs()
            setupPersistenceStubs()
        }

        @JvmStatic
        private fun setupVkFacadeStubs() {
            // Default stub for user profiles batch endpoint
            server!!.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/profiles/users/batch"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")
                    )
            )
            // Default stub for group profiles batch endpoint
            server!!.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/profiles/groups/batch"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")
                    )
            )
        }

        @JvmStatic
        private fun setupPersistenceStubs() {
            // Default stub for hasPendingSummary
            server!!.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/persistence/summaries/has-pending"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("false")
                    )
            )
            // Default stub for findLastSummary
            server!!.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/persistence/summaries/last"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(204)
                    )
            )
            // Default stub for findMessagesBefore
            server!!.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/persistence/messages/before"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")
                    )
            )
            // Default stub for findMessagesBetween
            server!!.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/persistence/messages/between"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")
                    )
            )
            // Default stub for createPendingSummary
            server!!.stubFor(
                WireMock.post(WireMock.urlPathEqualTo("/persistence/summaries/pending"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("\"${java.util.UUID.randomUUID()}\"")
                    )
            )
            // Default stub for saveCompletedSummary
            server!!.stubFor(
                WireMock.post(WireMock.urlPathMatching("/persistence/summaries/.*/complete"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(204)
                    )
            )
            // Default stub for markSummaryAsFailed
            server!!.stubFor(
                WireMock.post(WireMock.urlPathMatching("/persistence/summaries/.*/fail"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(204)
                    )
            )
            // Default stub for findUserProfilesByIds
            server!!.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/persistence/profiles/users"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")
                    )
            )
            // Default stub for findGroupProfilesByIds
            server!!.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/persistence/profiles/groups"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")
                    )
            )
        }
    }

    override fun start(): Map<String, String> {
        server = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        server!!.start()

        // Setup default stubs for internal services
        setupVkFacadeStubs()
        setupPersistenceStubs()

        return mapOf(
            // Override Quarkus REST client URL for VK API
            "quarkus.rest-client.vk.url" to "${server!!.baseUrl()}/method",
            // Override LangChain4j Ollama base URL
            "quarkus.langchain4j.ollama.base-url" to server!!.baseUrl(),
            // Override internal service URLs to use WireMock
            "quarkus.rest-client.vk-facade.url" to "${server!!.baseUrl()}",
            "quarkus.rest-client.persistence.url" to "${server!!.baseUrl()}"
        )
    }

    override fun stop() {
        server?.stop()
        server = null
    }
}
