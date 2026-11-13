package com.simarel.vkbot.integration

import io.quarkus.test.junit.QuarkusTestProfile

/**
 * Test profile for full integration tests.
 * Activates 'integration-test' configuration profile which enables:
 * - RabbitMQ devservices
 * - WireMock devservices for VK API and OpenAI-compatible (llama.cpp) mocking
 */
class IntegrationTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"
}
