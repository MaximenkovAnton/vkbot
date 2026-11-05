package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.adapter.input.vk.mapper.MessageMapper
import com.simarel.vkbot.receiver.domain.exception.VkEventPublishException
import com.simarel.vkbot.receiver.domain.vo.LongPollFailure
import com.simarel.vkbot.receiver.domain.vo.VkUpdateType
import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortRequest
import com.simarel.vkbot.receiver.port.output.CheckLongPollPort
import com.simarel.vkbot.receiver.port.output.GetLongPollServerPort
import com.simarel.vkbot.share.domain.vo.LongPollTs
import com.simarel.vkbot.share.port.output.persistence.FindLongPollTsPort
import com.simarel.vkbot.share.port.output.persistence.SaveLongPollTsPort
import io.quarkus.logging.Log
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.json.Json
import jakarta.json.JsonException
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.StringReader

@Suppress("LongParameterList")
@ApplicationScoped
open class LongPollRunner(
    private val getLongPollServerPort: GetLongPollServerPort,
    private val checkLongPollPort: CheckLongPollPort,
    private val receiveMessageInputPort: ReceiveMessageInputPort,
    private val findLongPollTsPort: FindLongPollTsPort,
    private val saveLongPollTsPort: SaveLongPollTsPort,
    private val messageMapper: MessageMapper,
    @ConfigProperty(name = "vk.group.id") private val groupId: Long,
    @ConfigProperty(name = "vk.longpoll.enabled", defaultValue = "true") private val enabled: Boolean,
) {
    @Volatile
    var pollThread: Thread? = null
        private set

    @Volatile
    private var running = false

    @Volatile
    private var session: GetLongPollServerPort.Response? = null

    @Volatile
    private var ts: String? = null

    @Suppress("UnusedParameter")
    fun start(@Observes event: StartupEvent) {
        if (!enabled) {
            Log.info("VK long poll is disabled")
            return
        }
        running = true
        Log.info("VK long poll started for group $groupId")
        pollThread = Thread.ofVirtual().name(POLL_THREAD_NAME).start(::loop)
    }

    @Suppress("UnusedParameter")
    fun stop(@Observes event: ShutdownEvent) {
        running = false
        pollThread?.interrupt()
    }

    open fun pollOnce() {
        val activeSession = session ?: renewSession()
        val response = checkLongPollPort.execute(
            CheckLongPollPort.Request(
                server = activeSession.server,
                key = activeSession.key,
                ts = restoreTs(activeSession),
                wait = WAIT_SECONDS,
            ),
        )
        when (response.failure) {
            LongPollFailure.NONE -> {
                response.updates.forEach { update -> applyUpdate(update) }
                response.ts?.let(::saveTs)
            }

            LongPollFailure.TS_OUTDATED -> {
                Log.warn("VK long poll reported an outdated ts, continuing from ${response.ts}")
                response.ts?.let(::saveTs)
            }

            LongPollFailure.KEY_EXPIRED -> {
                Log.warn("VK long poll key expired, requesting a new session")
                session = null
            }

            LongPollFailure.INFO_LOST -> {
                Log.warn("VK long poll info lost, requesting a new session")
                ts = renewSession().ts
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    open fun loop() {
        var failures = 0
        while (running) {
            try {
                pollOnce()
                failures = 0
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (e: VkEventPublishException) {
                Log.error(
                    "Publishing VK update failed (${e.cause?.javaClass?.name}), " +
                        "retrying in $PUBLISH_FAILURE_PAUSE_MILLIS ms",
                )
                pause(PUBLISH_FAILURE_PAUSE_MILLIS)
            } catch (e: Exception) {
                failures++
                val pauseMillis = networkPauseMillis(failures)
                Log.error("VK long poll request failed (${e.javaClass.name}), retrying in $pauseMillis ms")
                pause(pauseMillis)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun applyUpdate(rawUpdate: String) {
        val update = try {
            Json.createReader(StringReader(rawUpdate)).readObject()
        } catch (e: JsonException) {
            Log.error("Skipping unreadable VK update: ${e.message}")
            return
        }
        val type = update.getString("type", null)?.let { VkUpdateType.mapOrUnknown(it) } ?: VkUpdateType.UNKNOWN
        if (type !in HANDLED_TYPES) {
            Log.error("Skipping VK update of unknown type ${update.getString("type", null).orEmpty()}")
            return
        }
        val message = try {
            messageMapper.toDomain(update)
        } catch (e: Exception) {
            Log.error("Skipping VK update that could not be mapped (${e.javaClass.name})")
            return
        }
        try {
            receiveMessageInputPort.execute(VkUpdateInputPortRequest(type = type, message = message))
        } catch (e: Exception) {
            throw VkEventPublishException(e)
        }
    }

    private fun saveTs(newTs: String) {
        ts = newTs
        saveLongPollTsPort.execute(SaveLongPollTsPort.Request(LongPollTs(newTs)))
    }

    private fun renewSession(): GetLongPollServerPort.Response {
        val renewed = getLongPollServerPort.execute(GetLongPollServerPort.Request)
        session = renewed
        return renewed
    }

    private fun restoreTs(activeSession: GetLongPollServerPort.Response): String {
        ts?.let { return it }
        val restored = findLongPollTsPort.execute(FindLongPollTsPort.Request).ts?.value ?: activeSession.ts
        ts = restored
        return restored
    }

    private fun networkPauseMillis(failures: Int): Long {
        val shift = minOf(failures - 1, MAX_BACKOFF_SHIFT)
        return minOf(NETWORK_PAUSE_MIN_MILLIS shl shift, NETWORK_PAUSE_MAX_MILLIS)
    }

    internal open fun pause(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val POLL_THREAD_NAME = "vk-long-poll"
        const val WAIT_SECONDS = 25
        const val PUBLISH_FAILURE_PAUSE_MILLIS = 5_000L
        const val NETWORK_PAUSE_MIN_MILLIS = 5_000L
        const val NETWORK_PAUSE_MAX_MILLIS = 60_000L
        const val MAX_BACKOFF_SHIFT = 4
        val HANDLED_TYPES = setOf(VkUpdateType.MESSAGE_NEW, VkUpdateType.MESSAGE_REPLY)
    }
}
