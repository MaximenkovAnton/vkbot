package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.VkUserProfileHistory
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.VkUserProfiles
import com.simarel.vkbot.persistence.domain.BirthDateMatcher
import com.simarel.vkbot.persistence.domain.profile.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import java.sql.Timestamp
import java.time.MonthDay
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
open class JooqVkUserProfileRepository {

    @Inject
    lateinit var dsl: DSLContext

    @Inject
    lateinit var objectMapper: ObjectMapper

    private val logger = System.getLogger(JooqVkUserProfileRepository::class.java.name)

    open fun persist(profile: VkUserProfile, changes: Map<String, Map<String, Any?>>? = null) {
        dsl.insertInto(VkUserProfiles.TABLE)
            .columns(
                VkUserProfiles.ID,
                VkUserProfiles.FIRST_NAME,
                VkUserProfiles.LAST_NAME,
                VkUserProfiles.SCREEN_NAME,
                VkUserProfiles.BIRTH_DATE,
                VkUserProfiles.CITY,
                VkUserProfiles.PROFILE_DETAILS,
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
                JSONB.valueOf(objectMapper.writeValueAsString(profile.details)),
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
            .set(VkUserProfiles.PROFILE_DETAILS, JSONB.valueOf(objectMapper.writeValueAsString(profile.details)))
            .set(VkUserProfiles.LAST_UPDATED, Timestamp.from(profile.lastUpdated.toInstant()))
            .execute()

        if (!changes.isNullOrEmpty()) {
            persistHistory(profile.id.value, changes)
        }
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

    open fun findHistory(profileId: Long, limit: Int, offset: Int): List<ProfileHistoryEntry> {
        if (limit <= 0) {
            return emptyList()
        }
        return dsl.select()
            .from(VkUserProfileHistory.TABLE)
            .where(VkUserProfileHistory.PROFILE_ID.eq(profileId))
            .orderBy(VkUserProfileHistory.CHANGED_AT.desc(), VkUserProfileHistory.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map { record ->
                ProfileHistoryEntry(
                    profileId = profileId,
                    changedAt = record.get(VkUserProfileHistory.CHANGED_AT, OffsetDateTime::class.java),
                    changes = objectMapper.readValue(
                        record.get(VkUserProfileHistory.CHANGES, String::class.java),
                        object : TypeReference<Map<String, Any?>>() {},
                    ),
                )
            }
    }

    private fun persistHistory(profileId: Long, changes: Map<String, Map<String, Any?>>) {
        dsl.insertInto(VkUserProfileHistory.TABLE)
            .columns(VkUserProfileHistory.ID, VkUserProfileHistory.PROFILE_ID, VkUserProfileHistory.CHANGES)
            .values(
                UUID.randomUUID(),
                profileId,
                JSONB.valueOf(objectMapper.writeValueAsString(changes)),
            )
            .execute()
    }

    private fun Record.toVkUserProfile(): VkUserProfile = VkUserProfile(
        id = FromId.of(get(VkUserProfiles.ID, Long::class.java)),
        firstName = get(VkUserProfiles.FIRST_NAME, String::class.java),
        lastName = get(VkUserProfiles.LAST_NAME, String::class.java),
        screenName = get(VkUserProfiles.SCREEN_NAME, String::class.java),
        birthDate = get(VkUserProfiles.BIRTH_DATE, String::class.java),
        city = get(VkUserProfiles.CITY, String::class.java),
        lastUpdated = get(VkUserProfiles.LAST_UPDATED, OffsetDateTime::class.java),
        details = get(VkUserProfiles.PROFILE_DETAILS, String::class.java)
            ?.let { json -> objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {}) }
            ?: emptyMap(),
    )
}
