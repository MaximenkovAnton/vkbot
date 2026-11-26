package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.LongPollLease
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.time.Duration
import java.time.OffsetDateTime

@ApplicationScoped
open class LongPollLeaseTable {

    @Inject
    lateinit var dsl: DSLContext

    open fun acquire(owner: String, ttl: Duration): Boolean = dsl.insertInto(LongPollLease.TABLE)
        .columns(LongPollLease.ID, LongPollLease.OWNER, LongPollLease.LEASE_UNTIL)
        .values(DSL.`val`(LEASE_ID), DSL.`val`(owner), leaseUntil(ttl))
        .onConflict(LongPollLease.ID)
        .doUpdate()
        .set(LongPollLease.OWNER, owner)
        .set(LongPollLease.LEASE_UNTIL, leaseUntil(ttl))
        .where(
            DSL.field("long_poll_lease.lease_until", SQLDataType.TIMESTAMPWITHTIMEZONE)
                .lt(DSL.currentOffsetDateTime())
                .or(DSL.field("long_poll_lease.owner", SQLDataType.VARCHAR(OWNER_LENGTH)).eq(owner)),
        )
        .returningResult(LongPollLease.ID)
        .fetch()
        .isNotEmpty()

    open fun release(owner: String) {
        dsl.update(LongPollLease.TABLE)
            .set(LongPollLease.LEASE_UNTIL, DSL.currentOffsetDateTime())
            .where(LongPollLease.OWNER.eq(owner))
            .execute()
    }

    private fun leaseUntil(ttl: Duration): Field<OffsetDateTime> = DSL.field(
        "now() + {0} * interval '1 second'",
        SQLDataType.TIMESTAMPWITHTIMEZONE,
        ttl.seconds,
    )

    companion object {
        private const val LEASE_ID: Short = 1
        private const val OWNER_LENGTH = 64
    }
}
