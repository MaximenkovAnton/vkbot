package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.LongPollPosition
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext

@ApplicationScoped
open class LongPollPositionTable {

    @Inject
    lateinit var dsl: DSLContext

    open fun find(): String? {
        val record = dsl.select()
            .from(LongPollPosition.TABLE)
            .where(LongPollPosition.ID.eq(POSITION_ID))
            .fetchOne()
            ?: return null

        return record.get(LongPollPosition.TS, String::class.java)
    }

    open fun save(ts: String) {
        dsl.insertInto(LongPollPosition.TABLE)
            .columns(LongPollPosition.ID, LongPollPosition.TS)
            .values(POSITION_ID, ts)
            .onConflict(LongPollPosition.ID)
            .doUpdate()
            .set(LongPollPosition.TS, ts)
            .execute()
    }

    companion object {
        private const val POSITION_ID: Short = 1
    }
}
