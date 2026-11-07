package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.GreetingLog
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import java.sql.Date
import java.time.LocalDate

@ApplicationScoped
open class JooqGreetingLogRepository {

    @Inject
    lateinit var dsl: DSLContext

    open fun claim(date: LocalDate, peerId: Long): Boolean = dsl.insertInto(GreetingLog.TABLE)
        .columns(GreetingLog.GREETING_DATE, GreetingLog.PEER_ID)
        .values(Date.valueOf(date), peerId)
        .onConflict(GreetingLog.GREETING_DATE, GreetingLog.PEER_ID)
        .doNothing()
        .execute() == 1
}
