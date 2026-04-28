package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.domain.entity.Tables.VkUserProfiles
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ApplicationScoped
open class JooqVkUserProfileRepository {

    @Inject
    lateinit var dsl: DSLContext

    open fun persist(profile: VkUserProfile) {
        dsl.insertInto(VkUserProfiles.TABLE)
            .columns(
                VkUserProfiles.ID,
                VkUserProfiles.FIRST_NAME,
                VkUserProfiles.LAST_NAME,
                VkUserProfiles.SCREEN_NAME,
                VkUserProfiles.BIRTH_DATE,
                VkUserProfiles.CITY,
                VkUserProfiles.LAST_UPDATED,
                VkUserProfiles.CREATED_AT
            )
            .values(
                profile.id.value,
                profile.firstName,
                profile.lastName,
                profile.screenName,
                profile.birthDate,
                profile.city,
                Timestamp.from(profile.lastUpdated.toInstant()),
                Timestamp.from(OffsetDateTime.now().toInstant())
            )
            .onConflict(VkUserProfiles.ID)
            .doUpdate()
            .set(VkUserProfiles.FIRST_NAME, profile.firstName)
            .set(VkUserProfiles.LAST_NAME, profile.lastName)
            .set(VkUserProfiles.SCREEN_NAME, profile.screenName)
            .set(VkUserProfiles.BIRTH_DATE, profile.birthDate)
            .set(VkUserProfiles.CITY, profile.city)
            .set(VkUserProfiles.LAST_UPDATED, Timestamp.from(profile.lastUpdated.toInstant()))
            .execute()
    }

    open fun findByIds(ids: Collection<Long>): List<VkUserProfile> {
        if (ids.isEmpty()) {
            return emptyList()
        }

        return dsl.select()
            .from(VkUserProfiles.TABLE)
            .where(VkUserProfiles.ID.`in`(ids))
            .fetch()
            .map { record ->
                VkUserProfile(
                    id = FromId.of(record.get(VkUserProfiles.ID, Long::class.java)),
                    firstName = record.get(VkUserProfiles.FIRST_NAME, String::class.java),
                    lastName = record.get(VkUserProfiles.LAST_NAME, String::class.java),
                    screenName = record.get(VkUserProfiles.SCREEN_NAME, String::class.java),
                    birthDate = record.get(VkUserProfiles.BIRTH_DATE, String::class.java),
                    city = record.get(VkUserProfiles.CITY, String::class.java),
                    lastUpdated = record.get(VkUserProfiles.LAST_UPDATED, OffsetDateTime::class.java)
                )
            }
    }

    open fun filterExistingIds(ids: Collection<Long>): Set<Long> {
        if (ids.isEmpty()) {
            return emptySet()
        }

        return dsl.select(VkUserProfiles.ID)
            .from(VkUserProfiles.TABLE)
            .where(VkUserProfiles.ID.`in`(ids))
            .fetch()
            .map { it.get(VkUserProfiles.ID, Long::class.java) }
            .toSet()
    }
}
