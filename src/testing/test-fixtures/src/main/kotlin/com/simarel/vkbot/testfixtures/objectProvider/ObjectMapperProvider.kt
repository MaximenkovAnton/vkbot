package com.simarel.vkbot.testfixtures.objectProvider

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object ObjectMapperProvider {
    fun create(): ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        objectMapper.registerModule(KotlinModule.Builder().build())
        return objectMapper
    }
}