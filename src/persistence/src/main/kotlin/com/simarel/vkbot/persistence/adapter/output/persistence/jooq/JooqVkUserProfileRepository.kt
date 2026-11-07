package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.VkUserProfiles
import com.simarel.vkbot.persistence.domain.BirthDateMatcher
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.Record
import java.sql.Timestamp
import java.time.MonthDay
import java.time.OffsetDateTime

@ApplicationScoped
open class JooqVkUserProfileRepository {

    @Inject
    lateinit var dsl: DSLContext

    private val logger = System.getLogger(JooqVkUserProfileRepository::class.java.name)

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
                VkUserProfiles.CREATED_AT,
            )
            .values(
                profile.id.value,
                profile.firstName,
                profile.lastName,
                profile.screenName,
                profile.birthDate,
                profile.city,
                Timestamp.from(profile.lastUpdated.toInstant()),
                Timestamp.from(OffsetDateTime.now().toInstant()),
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
            .map { it.toVkUserProfile() }
    }

    open fun findByBirthDate(day: Int, month: Int): List<VkUserProfile> {
        val target = MonthDay.of(month, day)
        return dsl.select()
            .from(VkUserProfiles.TABLE)
            .fetch()
            .mapNotNull { record ->
                val profile = record.toVkUserProfile()
                val birthDate = BirthDateMatcher.parse(profile.birthDate)
                if (birthDate == null) {
                    logger.log(
                        System.Logger.Level.WARNING,
                        "Skipping user profile for id: ${profile.id.value}, invalid birth date: ${profile.birthDate}",
                    )
                }
                if (birthDate == target) profile else null
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

    private fun Record.toVkUserProfile(): VkUserProfile = VkUserProfile(
        id = FromId.of(get(VkUserProfiles.ID, Long::class.java)),
        firstName = get(VkUserProfiles.FIRST_NAME, String::class.java),
        lastName = get(VkUserProfiles.LAST_NAME, String::class.java),
        screenName = get(VkUserProfiles.SCREEN_NAME, String::class.java),
        birthDate = get(VkUserProfiles.BIRTH_DATE, String::class.java),
        city = get(VkUserProfiles.CITY, String::class.java),
        lastUpdated = get(VkUserProfiles.LAST_UPDATED, OffsetDateTime::class.java),
    )
}
