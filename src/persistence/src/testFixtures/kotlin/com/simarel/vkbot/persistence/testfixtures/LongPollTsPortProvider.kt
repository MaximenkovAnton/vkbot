package com.simarel.vkbot.persistence.testfixtures

import com.simarel.vkbot.share.domain.vo.LongPollTs
import com.simarel.vkbot.share.port.output.persistence.FindLongPollTsPort
import com.simarel.vkbot.share.port.output.persistence.SaveLongPollTsPort

object LongPollTsPortProvider {

    fun createFindLongPollTsRequest() = FindLongPollTsPort.Request

    fun createSaveLongPollTsRequest(
        ts: String = "ts-1",
    ) = SaveLongPollTsPort.Request(ts = LongPollTs(ts))
}
