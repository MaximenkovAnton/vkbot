package com.simarel.vkbot.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager

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
        }
    }

    override fun start(): Map<String, String> {
        server = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        server!!.start()

        return mapOf(
            "quarkus.rest-client.vk.url" to "${server!!.baseUrl()}/method",
            "quarkus.langchain4j.ollama.base-url" to server!!.baseUrl(),
        )
    }

    override fun stop() {
        server?.stop()
        server = null
    }
}
