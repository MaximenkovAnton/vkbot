package com.simarel.vkbot.processor.command.help

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.vo.MessageText
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

interface BuildHelpTextCommand :
    Command<
        BuildHelpTextCommand.BuildHelpTextRequest,
        BuildHelpTextCommand.BuildHelpTextResponse,
        > {
    data object BuildHelpTextRequest : CommandRequest
    data class BuildHelpTextResponse(val messageText: MessageText) : CommandResponse
}

@ApplicationScoped
open class BuildHelpTextCommandImpl(
    @ConfigProperty(name = "vk.bot.mention", defaultValue = "@simarel")
    private val botMention: String,
) : BuildHelpTextCommand {
    override fun execute(
        request: BuildHelpTextCommand.BuildHelpTextRequest,
    ): BuildHelpTextCommand.BuildHelpTextResponse = BuildHelpTextCommand.BuildHelpTextResponse(
        MessageText.of(
            """
            Я — бот сообщества. Вот что я умею:
            • «Что нового» — расскажу, что происходило в чате за выбранный срок. Примеры: «что нового за вчера», «что нового с 1 по 3 ноября». Если срок не указать, возьму время с вашего последнего сообщения, но не больше недели.
            • «Что ты умеешь», /help или /помощь — эта справка: что я умею и как меня вызвать.
            В групповом чате упомяните бота, например: $botMention что нового за вчера. Остальные вопросы получат обычный AI-ответ.
            """.trimIndent(),
        ),
    )
}
