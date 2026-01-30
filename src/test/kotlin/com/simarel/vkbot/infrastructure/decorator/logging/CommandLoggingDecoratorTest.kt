package com.simarel.vkbot.infrastructure.decorator.logging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.simarel.vkbot.objectProvider.fake.command.FakeCommand
import com.simarel.vkbot.objectProvider.fake.command.FakeCommandRequest
import com.simarel.vkbot.objectProvider.fake.command.FakeCommandResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandLoggingDecoratorTest {

    @Test
    fun `execute delegates to wrapped command`() {
        // Given
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val fakeCommand = FakeCommand()
        val decorator = CommandLoggingDecorator(fakeCommand, objectMapper)
        val request = FakeCommandRequest("test-value")
        val expectedResponse = FakeCommandResponse("test-result")
        fakeCommand.response = expectedResponse

        // When
        val result = decorator.execute(request)

        // Then
        assertEquals(1, fakeCommand.executeCalls.size)
        assertEquals(request, fakeCommand.executeCalls.first())
        assertEquals(expectedResponse, result)
    }

    @Test
    fun `execute passes through the same request and response`() {
        // Given
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val fakeCommand = FakeCommand()
        val decorator = CommandLoggingDecorator(fakeCommand, objectMapper)
        val request = FakeCommandRequest("input")
        val expectedResponse = FakeCommandResponse("output")
        fakeCommand.response = expectedResponse

        // When
        val result = decorator.execute(request)

        // Then
        assertEquals(request.value, fakeCommand.executeCalls.first().value)
        assertEquals(expectedResponse.result, result.result)
    }
}
