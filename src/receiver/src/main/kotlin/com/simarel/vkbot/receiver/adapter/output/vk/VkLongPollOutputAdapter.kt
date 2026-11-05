package com.simarel.vkbot.receiver.adapter.output.vk

import com.simarel.vkbot.receiver.adapter.output.vk.dto.VkLongPollServerResponseDto
import com.simarel.vkbot.receiver.domain.vo.LongPollFailure
import com.simarel.vkbot.receiver.port.output.CheckLongPollPort
import com.simarel.vkbot.receiver.port.output.GetLongPollServerPort
import io.quarkus.rest.client.reactive.ClientQueryParam
import jakarta.enterprise.context.ApplicationScoped
import jakarta.json.Json
import jakarta.json.JsonNumber
import jakarta.json.JsonString
import jakarta.json.JsonValue
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.RestClientBuilder
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.eclipse.microprofile.rest.client.inject.RestClient
import java.io.StringReader
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@ApplicationScoped
open class VkLongPollOutputAdapter(
    @RestClient private val longPollServerClient: VkLongPollServerClient,
    @ConfigProperty(name = "vk.group.id") private val groupId: Long,
) : GetLongPollServerPort {

    override fun execute(request: GetLongPollServerPort.Request): GetLongPollServerPort.Response {
        val session = longPollServerClient.getServer(groupId).response
            ?: error("VK returned no long poll server session")
        return GetLongPollServerPort.Response(
            server = session.server,
            key = session.key,
            ts = session.ts.asText(),
        )
    }
}

@ApplicationScoped
open class VkLongPollCheckOutputAdapter : CheckLongPollPort {

    private val clients = ConcurrentHashMap<String, VkLongPollCheckClient>()

    override fun execute(request: CheckLongPollPort.Request): CheckLongPollPort.Response {
        val body = checkClient(request.server).check(
            act = CHECK_ACTION,
            key = request.key,
            ts = request.ts,
            wait = request.wait,
        )
        return parseCheckResponse(body)
    }

    private fun checkClient(server: String): VkLongPollCheckClient = clients.computeIfAbsent(server) {
        RestClientBuilder.newBuilder()
            .baseUri(URI.create(server))
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build(VkLongPollCheckClient::class.java)
    }

    internal fun parseCheckResponse(body: String): CheckLongPollPort.Response {
        val json = Json.createReader(StringReader(body)).readObject()
        return CheckLongPollPort.Response(
            ts = tsOf(json.get("ts")),
            updates = json.getJsonArray("updates")?.map { it.asJsonObject().toString() } ?: emptyList(),
            failure = failureOf(json.getInt("failed", 0)),
        )
    }

    private fun tsOf(ts: JsonValue?): String? = when {
        ts == null -> null
        ts.valueType == JsonValue.ValueType.STRING -> (ts as JsonString).string
        ts.valueType == JsonValue.ValueType.NUMBER -> (ts as JsonNumber).toString()
        else -> null
    }

    private fun failureOf(code: Int): LongPollFailure = when (code) {
        TS_OUTDATED_CODE -> LongPollFailure.TS_OUTDATED
        KEY_EXPIRED_CODE -> LongPollFailure.KEY_EXPIRED
        INFO_LOST_CODE -> LongPollFailure.INFO_LOST
        else -> LongPollFailure.NONE
    }

    private companion object {
        const val CHECK_ACTION = "a_check"
        const val READ_TIMEOUT_SECONDS = 60L
        const val TS_OUTDATED_CODE = 1
        const val KEY_EXPIRED_CODE = 2
        const val INFO_LOST_CODE = 3
    }
}

@RegisterRestClient(configKey = "vk")
@ClientQueryParam(name = "v", value = ["\${vk.api.version}"])
interface VkLongPollServerClient {

    @POST
    @Path("/groups.getLongPollServer")
    fun getServer(@FormParam("group_id") groupId: Long): VkLongPollServerResponseDto
}

interface VkLongPollCheckClient {

    @GET
    fun check(
        @QueryParam("act") act: String,
        @QueryParam("key") key: String,
        @QueryParam("ts") ts: String,
        @QueryParam("wait") wait: Int,
    ): String
}
