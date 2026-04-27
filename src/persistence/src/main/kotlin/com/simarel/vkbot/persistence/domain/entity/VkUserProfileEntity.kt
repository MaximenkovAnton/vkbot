package com.simarel.vkbot.persistence.domain.entity

import io.quarkus.hibernate.orm.panache.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "vk_user_profiles")
open class VkUserProfileEntity : PanacheEntityBase() {
    @Id
    open var id: Long? = null

    @Column(name = "first_name", columnDefinition = "TEXT")
    open var firstName: String? = null

    @Column(name = "last_name", columnDefinition = "TEXT")
    open var lastName: String? = null

    @Column(name = "screen_name", columnDefinition = "TEXT")
    open var screenName: String? = null

    @Column(name = "birth_date", columnDefinition = "TEXT")
    open var birthDate: String? = null

    @Column(name = "city", columnDefinition = "TEXT")
    open var city: String? = null

    @Column(name = "last_updated", nullable = false)
    open var lastUpdated: OffsetDateTime? = null

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime? = null
}
