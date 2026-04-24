package com.simarel.vkbot.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager

/**
 * Quarkus test resource that starts WireMock server for integration tests.
 * WireMock is used to mock external HTTP services:
 * - VK API (api.vk.ru/method)
 * - Ollama API (for LangChain4j)
 */
class WireMockTestResource : QuarkusTestResourceLifecycleManager {

    companion object {
        @JvmStatic
        private var server: WireMockServer? = null

        @JvmStatic
        val wireMockServer: WireMockServer
            get() = server
                ?: throw IllegalStateException("WireMock server not initialized. Ensure @QuarkusTestResource is applied.")
    }

    override fun start(): Map<String, String> {
        server = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        server!!.start()

        return mapOf(
            // Override Quarkus REST client URL for VK API
            "quarkus.rest-client.vk.url" to "${server!!.baseUrl()}/method",
            // Override LangChain4j Ollama base URL
            "quarkus.langchain4j.ollama.base-url" to server!!.baseUrl()
        )
    }

    override fun stop() {
        server?.stop()
        server = null
    }
}
