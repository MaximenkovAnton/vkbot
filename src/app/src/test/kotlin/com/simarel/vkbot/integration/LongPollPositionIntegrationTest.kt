package com.simarel.vkbot.integration

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.simarel.vkbot.receiver.adapter.input.vk.LongPollRunner
import com.simarel.vkbot.receiver.adapter.input.vk.mapper.MessageMapper
import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.output.CheckLongPollPort
import com.simarel.vkbot.receiver.port.output.GetLongPollServerPort
import com.simarel.vkbot.share.port.output.persistence.FindLongPollTsPort
import com.simarel.vkbot.share.port.output.persistence.SaveLongPollTsPort
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.ResultSet
import javax.sql.DataSource

class LongPollPositionTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf("vk.longpoll.enabled" to "false")
}

@QuarkusTest
@TestProfile(LongPollPositionTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class LongPollPositionIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var getLongPollServerPort: GetLongPollServerPort

    @Inject
    lateinit var checkLongPollPort: CheckLongPollPort

    @Inject
    lateinit var receiveMessageInputPort: ReceiveMessageInputPort

    @Inject
    lateinit var findLongPollTsPort: FindLongPollTsPort

    @Inject
    lateinit var saveLongPollTsPort: SaveLongPollTsPort

    @Inject
    lateinit var messageMapper: MessageMapper

    @BeforeEach
    fun setUp() {
        WireMockTestResource.resetToDefaultStubs()
    }

    @Test
    fun `poll continues from the ts stored in postgres`() {
        seedSavedTs("55")
        stubLongPollSession(ts = "1")
        stubPoll(ts = "56")

        runner().pollOnce()

        WireMockTestResource.wireMockServer.verify(
            getRequestedFor(urlPathEqualTo(POLL_PATH)).withQueryParam("ts", equalTo("55")),
        )
        assertEquals("56", savedTs())
    }

    private fun runner(): LongPollRunner = LongPollRunner(
        getLongPollServerPort = getLongPollServerPort,
        checkLongPollPort = checkLongPollPort,
        receiveMessageInputPort = receiveMessageInputPort,
        findLongPollTsPort = findLongPollTsPort,
        saveLongPollTsPort = saveLongPollTsPort,
        messageMapper = messageMapper,
        groupId = GROUP_ID,
        enabled = false,
    )

    private fun seedSavedTs(ts: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SAVE_TS_SQL).use { statement ->
                statement.setString(1, ts)
                statement.setString(2, ts)
                statement.executeUpdate()
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
        val server = "${WireMockTestResource.wireMockServer.baseUrl()}$POLL_PATH"
        WireMockTestResource.wireMockServer.stubFor(
            post(urlPathEqualTo("/method/groups.getLongPollServer"))
                .willReturn(jsonResponse("""{"response":{"key":"test-key","server":"$server","ts":"$ts"}}""")),
        )
    }

    private fun stubPoll(ts: String) {
        WireMockTestResource.wireMockServer.stubFor(
            get(urlPathEqualTo(POLL_PATH))
                .willReturn(jsonResponse("""{"ts":"$ts","updates":[]}""")),
        )
    }

    private fun jsonResponse(body: String) = aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(body)

    private companion object {
        const val GROUP_ID = 232142875L
        const val POLL_PATH = "/whp/1"
        const val SAVE_TS_SQL =
            "INSERT INTO long_poll_position (id, ts) VALUES (1, ?) ON CONFLICT (id) DO UPDATE SET ts = ?"
    }
}
