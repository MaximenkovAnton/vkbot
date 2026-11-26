package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.port.input.PurgeInboxInputPort
import java.time.OffsetDateTime

object FakePurgeInboxInputPortProvider {

    private val DEFAULT_NOW: OffsetDateTime = OffsetDateTime.parse("2026-11-26T10:00:00Z")

    fun createRequest(now: OffsetDateTime = DEFAULT_NOW) = PurgeInboxInputPort.Request(now = now)

    fun createResponse(deleted: Int = 2) = PurgeInboxInputPort.Response(deleted = deleted)
}
