package com.simarel.vkbot.integration

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.awaitility.Awaitility.await
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Full integration tests for VK Webhook callback endpoint.
 * Tests the complete flow: webhook -> RabbitMQ -> processor -> RabbitMQ -> VK API
 *
 * Requires:
 * - RabbitMQ devservices (automatically started by Quarkus)
 * - WireMock for mocking VK API and Ollama (started via @QuarkusTestResource)
 */
@QuarkusTest
@TestProfile(IntegrationTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class VkWebhookIntegrationTest {

    companion object {
        const val TEST_SECRET = "test_secret"
        const val CONFIRMATION_CODE = "123456"
    }

    @BeforeEach
    fun setup() {
        // Reset WireMock before each test
        WireMockTestResource.wireMockServer.resetAll()
    }

    @Test
    fun `full flow - message_new event is processed end to end with VK API call`() {
        // Given: setup WireMock stubs for Ollama API (LangChain4j uses /api/chat endpoint)
        // For non-streaming requests (stream: false), Ollama returns a single JSON response
        WireMockTestResource.wireMockServer.stubFor(
            post(urlPathEqualTo("/api/chat"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "model": "llama3.2",
                                "created_at": "2024-01-01T00:00:00Z",
                                "message": {
                                    "role": "assistant",
                                    "content": "Hello! This is a response from AI."
                                },
                                "done": true,
                                "total_duration": 1234567890,
                                "load_duration": 12345678,
                                "prompt_eval_count": 50,
                                "prompt_eval_duration": 12345678,
                                "eval_count": 20,
                                "eval_duration": 1234567890
                            }
                            """.trimIndent()
                        )
                )
        )

        // Given: setup WireMock stub for VK API
        WireMockTestResource.wireMockServer.stubFor(
            post(urlPathEqualTo("/method/messages.send"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"response": 12345}""")
                )
        )

        // When: send webhook with message_new event
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                    "group_id": 232142875,
                    "type": "message_new",
                    "event_id": "fef1f1789eb2bd7c8cf147b5c7da926491320342",
                    "v": "5.199",
                    "object": {
                        "client_info": {
                            "button_actions": ["text", "vkpay"],
                            "keyboard": true,
                            "lang_id": 0
                        },
                        "message": {
                            "date": 1756381457,
                            "from_id": 173308266,
                            "id": 0,
                            "version": 10000390,
                            "out": 0,
                            "text": "Привет, бот! Как дела?",
                            "peer_id": 2000000001,
                            "random_id": 0,
                            "conversation_message_id": 2670
                        }
                    },
                    "secret": "$TEST_SECRET"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/vk/callback")
            .then()
            .statusCode(200)
            .contentType(ContentType.TEXT)
            .body(equalTo("ok"))

        // Then: wait for async processing through RabbitMQ and verify VK API was called
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .untilAsserted {
                WireMockTestResource.wireMockServer.verify(
                    postRequestedFor(urlPathEqualTo("/method/messages.send"))
                        .withRequestBody(containing("peer_id"))
                        .withRequestBody(containing("2000000001"))
                )
            }
    }

    @Test
    fun `POST vk callback with confirmation event returns confirmation code`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                    "group_id": 232142875,
                    "event_id": "2abd3fd8864e70eb085026e790635ef25067b93c",
                    "v": "5.199",
                    "type": "confirmation",
                    "secret": "$TEST_SECRET"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/vk/callback")
            .then()
            .statusCode(200)
            .contentType(ContentType.TEXT)
            .body(equalTo(CONFIRMATION_CODE))
    }

    @Test
    fun `POST vk callback with unknown event type returns ok`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                    "group_id": 232142875,
                    "event_id": "2abd3fd8864e70eb085026e790635ef25067b93c",
                    "v": "5.199",
                    "type": "bla-bla-bla",
                    "secret": "$TEST_SECRET"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/vk/callback")
            .then()
            .statusCode(200)
            .contentType(ContentType.TEXT)
            .body(equalTo("ok"))
    }

    @Test
    fun `POST vk callback with incorrect secret returns 403 Forbidden`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                    "group_id": 232142875,
                    "event_id": "2abd3fd8864e70eb085026e790635ef25067b93c",
                    "v": "5.199",
                    "type": "message_new",
                    "secret": "incorrect secret"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/vk/callback")
            .then()
            .statusCode(403)
            .contentType(ContentType.JSON)
            .body("error", equalTo("Secret code not provided or incorrect"))
    }

    @Test
    fun `POST vk callback without secret returns 403 Forbidden`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                    "group_id": 232142875,
                    "event_id": "2abd3fd8864e70eb085026e790635ef25067b93c",
                    "v": "5.199",
                    "type": "confirmation"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/vk/callback")
            .then()
            .statusCode(403)
            .contentType(ContentType.JSON)
            .body("error", equalTo("Secret code not provided or incorrect"))
    }

    @Test
    fun `VK API failure should handle error gracefully`() {
        // Given: VK API returns error 500
        WireMockTestResource.wireMockServer.stubFor(
            post(urlPathEqualTo("/method/messages.send"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
        )

        // When: send webhook with message_new
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                    "group_id": 232142875,
                    "type": "message_new",
                    "event_id": "test-event-id",
                    "v": "5.199",
                    "object": {
                        "client_info": {"keyboard": true, "lang_id": 0},
                        "message": {
                            "date": 1756381457,
                            "from_id": 173308266,
                            "id": 0,
                            "text": "Тестовое сообщение",
                            "peer_id": 2000000002,
                            "random_id": 0,
                            "conversation_message_id": 2671
                        }
                    },
                    "secret": "$TEST_SECRET"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/vk/callback")
            .then()
            .statusCode(200)
            .body(equalTo("ok"))
    }
}
