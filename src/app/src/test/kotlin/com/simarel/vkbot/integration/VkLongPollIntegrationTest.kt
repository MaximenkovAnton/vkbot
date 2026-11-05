package com.simarel.vkbot.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.ResultSet
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@TestProfile(IntegrationTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class VkLongPollIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    private val wireMock: WireMockServer
        get() = WireMockTestResource.wireMockServer

    @BeforeEach
    fun setUp() {
        WireMockTestResource.resetToDefaultStubs()
    }

    @Test
    fun `message new received by long poll is answered through vk api`() {
        stubVkApi()
        stubLongPollSession(ts = "1")
        stubFirstPoll(ANSWER_SCENARIO, messageNewBatch())
        stubFollowingPolls(ANSWER_SCENARIO, emptyBatch("2"))

        await().atMost(120, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).untilAsserted {
            wireMock.verify(
                postRequestedFor(urlPathEqualTo("/method/messages.send"))
                    .withRequestBody(containing("peer_id"))
                    .withRequestBody(containing("2000000001")),
            )
        }
    }

    @Test
    fun `callback endpoint is not served anymore`() {
        given()
            .contentType(ContentType.JSON)
            .body(messageNewBatchUpdate())
            .`when`()
            .post("/vk/callback")
            .then()
            .statusCode(404)
    }

    @Test
    fun `failed 3 opens a new session and polls from the ts of that session`() {
        stubLongPollSession(ts = "77")
        stubFirstPoll(FAILED_SCENARIO, """{"failed":3}""")
        stubFollowingPolls(FAILED_SCENARIO, emptyBatch("77"))

        await().atMost(60, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted {
            wireMock.verify(postRequestedFor(urlPathEqualTo("/method/groups.getLongPollServer")))
            wireMock.verify(
                getRequestedFor(urlPathEqualTo(POLL_PATH)).withQueryParam("ts", equalTo("77")),
            )
        }
    }

    @Test
    fun `poll position is saved after a batch and used by the next poll`() {
        clearSavedPosition()
        stubLongPollSession(ts = "1")
        stubFirstPoll(POSITION_SCENARIO, emptyBatch("2"))
        stubFollowingPolls(POSITION_SCENARIO, emptyBatch("2"))

        await().atMost(60, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted {
            assertEquals("2", savedTs())
            wireMock.verify(
                getRequestedFor(urlPathEqualTo(POLL_PATH)).withQueryParam("ts", equalTo("2")),
            )
        }
    }

    @Test
    fun `unknown update type is skipped and polling continues`() {
        stubLongPollSession(ts = "1")
        stubFirstPoll(UNKNOWN_SCENARIO, unknownUpdateBatch)
        stubFollowingPolls(UNKNOWN_SCENARIO, emptyBatch("5"))

        await().atMost(60, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted {
            wireMock.verify(
                getRequestedFor(urlPathEqualTo(POLL_PATH)).withQueryParam("ts", equalTo("5")),
            )
        }
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/method/messages.send")))
    }

    private fun clearSavedPosition() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM long_poll_position")
            }
        }
    }

    private fun savedTs(): String? = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT ts FROM long_poll_position").use { rows -> rows.savedTsOrNull() }
        }
    }

    private fun ResultSet.savedTsOrNull(): String? = if (next()) getString("ts") else null

    private fun stubLongPollSession(ts: String) {
        val server = "${wireMock.baseUrl()}$POLL_PATH"
        wireMock.stubFor(
            post(urlPathEqualTo("/method/groups.getLongPollServer"))
                .willReturn(jsonResponse("""{"response":{"key":"test-key","server":"$server","ts":"$ts"}}""")),
        )
    }

    private fun stubFirstPoll(scenario: String, body: String) {
        wireMock.stubFor(
            get(urlPathEqualTo(POLL_PATH))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(jsonResponse(body))
                .willSetStateTo(POLLED_STATE),
        )
    }

    private fun stubFollowingPolls(scenario: String, body: String) {
        wireMock.stubFor(
            get(urlPathEqualTo(POLL_PATH))
                .inScenario(scenario)
                .whenScenarioStateIs(POLLED_STATE)
                .willReturn(jsonResponse(body).withFixedDelay(POLL_DELAY_MILLIS)),
        )
    }

    private fun stubVkApi() {
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/chat/completions")).willReturn(jsonResponse(CHAT_COMPLETION_REPLY)),
        )
        wireMock.stubFor(
            post(urlPathEqualTo("/method/users.get")).willReturn(jsonResponse(USER_PROFILE_REPLY)),
        )
        wireMock.stubFor(
            post(urlPathEqualTo("/method/groups.getById")).willReturn(jsonResponse(GROUP_PROFILE_REPLY)),
        )
        wireMock.stubFor(
            post(urlPathEqualTo("/method/messages.send"))
                .willReturn(jsonResponse("""{"response":12345}""")),
        )
    }

    private fun jsonResponse(body: String): ResponseDefinitionBuilder = aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(body)

    private val unknownUpdateBatch = """
        {"ts":"5","updates":[
            {"type":"bla-bla-bla","event_id":"3","v":"5.199","object":{},"group_id":232142875}
        ]}
    """.trimIndent()

    private fun emptyBatch(ts: String): String = """{"ts":"$ts","updates":[]}"""

    private fun messageNewBatch(): String = """{"ts":"2","updates":[${messageNewBatchUpdate()}]}"""

    private fun messageNewBatchUpdate(): String = """
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
                    "text": "@simarel Привет, бот! Как дела?",
                    "peer_id": 2000000001,
                    "random_id": 0,
                    "conversation_message_id": 2670
                }
            }
        }
    """.trimIndent()

    private companion object {
        const val POLL_PATH = "/whp/1"
        const val POLLED_STATE = "polled"
        const val POLL_DELAY_MILLIS = 1000
        const val ANSWER_SCENARIO = "answer"
        const val FAILED_SCENARIO = "failed"
        const val POSITION_SCENARIO = "position"
        const val UNKNOWN_SCENARIO = "unknown"
        const val CHAT_COMPLETION_REPLY = """
            {
                "id": "chatcmpl-test",
                "object": "chat.completion",
                "created": 1735689600,
                "model": "test-model",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "Hello! This is a response from AI."
                        },
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 50,
                    "completion_tokens": 20,
                    "total_tokens": 70
                }
            }
        """
        const val USER_PROFILE_REPLY = """
            {
                "response": [
                    {
                        "id": 173308266,
                        "first_name": "Test",
                        "last_name": "User",
                        "screen_name": "testuser",
                        "bdate": "01.01.1990",
                        "city": {"id": 1, "title": "Moscow"}
                    }
                ]
            }
        """
        const val GROUP_PROFILE_REPLY = """
            {
                "response": [
                    {
                        "id": 1,
                        "name": "Test Group",
                        "screen_name": "testgroup"
                    }
                ]
            }
        """
    }
}
