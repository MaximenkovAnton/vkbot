package com.simarel.vkbot.persistence.domain.entity

import io.quarkus.hibernate.orm.panache.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.time.OffsetDateTime

@Entity
@Table(name = "vk_user_profiles")
open class VkUserProfileEntity : PanacheEntityBase() {
    @Id
    open var id: Long? = null

    @Column(name = "first_name")
    @JdbcTypeCode(Types.LONGVARCHAR)
    open var firstName: String? = null

    @Column(name = "last_name")
    @JdbcTypeCode(Types.LONGVARCHAR)
    open var lastName: String? = null

    @Column(name = "screen_name")
    @JdbcTypeCode(Types.LONGVARCHAR)
    open var screenName: String? = null

    @Column(name = "birth_date")
    @JdbcTypeCode(Types.LONGVARCHAR)
    open var birthDate: String? = null

    @Column(name = "city")
    @JdbcTypeCode(Types.LONGVARCHAR)
    open var city: String? = null

    @Column(name = "last_updated", nullable = false)
    @JdbcTypeCode(Types.TIMESTAMP_WITH_TIMEZONE)
    open var lastUpdated: OffsetDateTime? = null

    @Column(name = "created_at", nullable = false)
    @JdbcTypeCode(Types.TIMESTAMP_WITH_TIMEZONE)
    open var createdAt: OffsetDateTime? = null

}
