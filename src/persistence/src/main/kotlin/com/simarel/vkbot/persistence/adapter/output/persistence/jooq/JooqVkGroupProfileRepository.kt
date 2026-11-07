package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.VkGroupProfiles
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.Record
import java.sql.Timestamp
import java.time.OffsetDateTime

@ApplicationScoped
open class JooqVkGroupProfileRepository {

    @Inject
    lateinit var dsl: DSLContext

    open fun persist(profile: VkGroupProfile) {
        dsl.insertInto(VkGroupProfiles.TABLE)
            .columns(
                VkGroupProfiles.ID,
                VkGroupProfiles.NAME,
                VkGroupProfiles.SCREEN_NAME,
                VkGroupProfiles.LAST_UPDATED,
                VkGroupProfiles.CREATED_AT,
            )
            .values(
                profile.id.value,
                profile.name,
                profile.screenName,
                Timestamp.from(profile.lastUpdated.toInstant()),
                Timestamp.from(OffsetDateTime.now().toInstant()),
            )
            .onConflict(VkGroupProfiles.ID)
            .doUpdate()
            .set(VkGroupProfiles.NAME, profile.name)
            .set(VkGroupProfiles.SCREEN_NAME, profile.screenName)
            .set(VkGroupProfiles.LAST_UPDATED, Timestamp.from(profile.lastUpdated.toInstant()))
            .execute()
    }

    open fun findByIds(ids: Collection<Long>): List<VkGroupProfile> {
        if (ids.isEmpty()) {
            return emptyList()
        }

        return dsl.select()
            .from(VkGroupProfiles.TABLE)
            .where(VkGroupProfiles.ID.`in`(ids))
            .fetch()
            .map { it.toVkGroupProfile() }
    }

    private fun Record.toVkGroupProfile(): VkGroupProfile = VkGroupProfile(
        id = FromId.of(get(VkGroupProfiles.ID, Long::class.java)),
        name = get(VkGroupProfiles.NAME, String::class.java),
        screenName = get(VkGroupProfiles.SCREEN_NAME, String::class.java),
        lastUpdated = get(VkGroupProfiles.LAST_UPDATED, OffsetDateTime::class.java),
    )
}
