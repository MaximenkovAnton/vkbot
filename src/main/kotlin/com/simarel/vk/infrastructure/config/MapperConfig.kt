package com.simarel.vk.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.inject.Singleton

@Singleton
class AddKotlinModuleCustomizer : ObjectMapperCustomizer {

    override fun customize(objectMapper: ObjectMapper) {
        objectMapper.registerModule(KotlinModule.Builder().build())
    }
}