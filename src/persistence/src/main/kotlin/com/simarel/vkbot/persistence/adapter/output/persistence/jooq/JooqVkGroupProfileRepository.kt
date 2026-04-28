package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.domain.entity.Tables.VkGroupProfiles
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import java.sql.Timestamp
import java.time.OffsetDateTime

@ApplicationScoped
open class JooqVkGroupProfileRepository {

    @Inject
    lateinit var dsl: DSLContext

    open fun persist(profile: VkGroupProfile) {
        // Группы хранятся в БД с положительным ID
        val positiveId = profile.id.value

        dsl.insertInto(VkGroupProfiles.TABLE)
            .columns(
                VkGroupProfiles.ID,
                VkGroupProfiles.NAME,
                VkGroupProfiles.SCREEN_NAME,
                VkGroupProfiles.LAST_UPDATED,
                VkGroupProfiles.CREATED_AT
            )
            .values(
                positiveId,
                profile.name,
                profile.screenName,
                Timestamp.from(profile.lastUpdated.toInstant()),
                Timestamp.from(OffsetDateTime.now().toInstant())
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

        // FromId для групп < 0, но в БД хранятся > 0
        val positiveIds = ids.map { -it }

        return dsl.select()
            .from(VkGroupProfiles.TABLE)
            .where(VkGroupProfiles.ID.`in`(positiveIds))
            .fetch()
            .map { record ->
                val lastUpdated = record.get(VkGroupProfiles.LAST_UPDATED, OffsetDateTime::class.java)
                    ?: OffsetDateTime.now()

                VkGroupProfile(
                    // Конвертируем положительный ID в отрицательный
                    id = FromId.of(-record.get(VkGroupProfiles.ID, Long::class.java)),
                    name = record.get(VkGroupProfiles.NAME, String::class.java),
                    screenName = record.get(VkGroupProfiles.SCREEN_NAME, String::class.java),
                    lastUpdated = lastUpdated
                )
            }
    }

    open fun filterExistingIds(ids: Collection<Long>): Set<Long> {
        if (ids.isEmpty()) {
            return emptySet()
        }

        // FromId для групп < 0, но в БД хранятся > 0
        val positiveIds = ids.map { -it }

        return dsl.select(VkGroupProfiles.ID)
            .from(VkGroupProfiles.TABLE)
            .where(VkGroupProfiles.ID.`in`(positiveIds))
            .fetch()
            .map { -it.get(VkGroupProfiles.ID, Long::class.java) }
            .toSet()
    }
}
