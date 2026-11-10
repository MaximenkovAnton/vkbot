package com.simarel.vkbot.ai.domain.profile

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private const val BOT_ID = 9_000_001L
private const val BOT_MENTION = "@simarel"
private const val GROUP_ID = 232_142_875L

class ProfileTargetResolverTest {

    @ParameterizedTest
    @CsvSource("id7600001, 7600001", "club987654321, -987654321", "public987654321, -987654321")
    fun `resolves a bare mention token`(token: String, expectedId: Long) {
        val resolution = resolver().resolveExplicit(message("что ты знаешь о $token"))

        assertEquals(
            expectedTarget(expectedId),
            (resolution as ProfileTargetResolver.Resolution.Resolved).target,
        )
    }

    @Test
    fun `ignores the zero id token`() {
        assertEquals(
            ProfileTargetResolver.Resolution.NoTarget,
            resolver().resolveExplicit(message("что ты знаешь о id0")),
        )
    }

    @Test
    fun `collapses duplicate mentions of the same id`() {
        assertEquals(
            ProfileTargetResolver.Resolution.Resolved(ProfileTarget.User(FakeVoProvider.createFromId(7600001))),
            resolver().resolveExplicit(message("id7600001 [id7600001|@pasha]")),
        )
    }

    private fun expectedTarget(fromId: Long): ProfileTarget = if (fromId > 0) {
        ProfileTarget.User(FakeVoProvider.createFromId(fromId))
    } else {
        ProfileTarget.Group(FakeVoProvider.createFromId(fromId))
    }

    private fun resolver(): ProfileTargetResolver = ProfileTargetResolver(
        botId = BOT_ID,
        botMention = BOT_MENTION,
        groupId = GROUP_ID,
        nameNormalizer = ProfileNameNormalizer(BOT_MENTION),
        nameMatcher = ProfileNameMatcher(),
    )

    private fun message(text: String): Message = FakeVoProvider.createMessage(
        messageText = FakeVoProvider.createMessageText(text),
    )
}
