package com.simarel.vkbot.persistence.domain.entity

import io.quarkus.hibernate.orm.panache.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "vk_group_profiles")
open class VkGroupProfileEntity : PanacheEntityBase() {
    @Id
    open var id: Long? = null

    @Column(name = "name", columnDefinition = "TEXT")
    open var name: String? = null

    @Column(name = "screen_name", columnDefinition = "TEXT")
    open var screenName: String? = null

    @Column(name = "last_updated", nullable = false)
    open var lastUpdated: OffsetDateTime? = null

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime? = null
}
