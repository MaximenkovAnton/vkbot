package com.simarel.vkbot.persistence.config

import io.agroal.api.AgroalDataSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

@ApplicationScoped
open class JooqConfig {

    @Inject
    lateinit var dataSource: AgroalDataSource

    @Produces
    @ApplicationScoped
    open fun dslContext(): DSLContext {
        return DSL.using(dataSource, SQLDialect.POSTGRES)
    }
}
