package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.domain.vo.LongPollFailure
import com.simarel.vkbot.receiver.port.output.CheckLongPollPort
import com.simarel.vkbot.receiver.port.output.GetLongPollServerPort
import com.simarel.vkbot.share.domain.vo.LongPollTs

object FakeLongPollPortProvider {

    fun createSession(
        server: String = "https://lp.vk.com/wh1",
        key: String = "test-key",
        ts: String = "ts-1",
    ) = GetLongPollServerPort.Response(server = server, key = key, ts = ts)

    fun createCheckResponse(
        ts: String? = "ts-2",
        updates: List<String> = emptyList(),
        failure: LongPollFailure = LongPollFailure.NONE,
    ) = CheckLongPollPort.Response(ts = ts, updates = updates, failure = failure)

    fun createLongPollTs(ts: String = "ts-1") = LongPollTs(ts)
}
