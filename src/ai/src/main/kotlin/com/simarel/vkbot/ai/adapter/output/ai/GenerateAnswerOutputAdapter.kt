package com.simarel.vkbot.ai.adapter.output.ai

import com.simarel.vkbot.ai.port.output.ai.ChatMessage
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPort
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortRequest
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortResponse
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GenerateAnswerOutputAdapter(
    private val userAnswerAiService: UserAnswerAiService,
) : GenerateAnswerOutputPort {

    override fun execute(request: GenerateAnswerOutputPortRequest): GenerateAnswerOutputPortResponse {
        val answer = userAnswerAiService.generateAnswer(
            userProfiles = formatUserProfiles(request.userProfiles),
            groupProfiles = formatGroupProfiles(request.groupProfiles),
            chatHistory = formatChatHistory(request.chatHistory, request.userProfiles, request.groupProfiles),
            currentMessage = formatCurrentMessage(request.currentMessage, request.userProfiles, request.groupProfiles),
        )
        return GenerateAnswerOutputPortResponse(answer)
    }

    private fun formatChatHistory(
        history: List<ChatMessage>,
        userProfiles: Map<FromId, VkUserProfile>,
        groupProfiles: Map<FromId, VkGroupProfile>
    ): String {
        return history.joinToString("\n") { msg ->
            val author = formatAuthor(msg.fromId, userProfiles, groupProfiles)
            buildString {
                appendLine("[MESSAGE_START]")
                appendLine("ID: ${msg.id.value} | Отправитель: $author")
                appendLine("""Текст: "${msg.text.value}""".trimIndent())
                append("[MESSAGE_END]")
            }
        }
    }

    private fun formatCurrentMessage(
        message: Message,
        userProfiles: Map<FromId, VkUserProfile>,
        groupProfiles: Map<FromId, VkGroupProfile>
    ): String {
        val author = formatAuthor(message.fromId, userProfiles, groupProfiles)
        val forwardedBlock = if (message.forwardedMessages.isNotEmpty()) {
            formatForwardedBlock(message.forwardedMessages, userProfiles, groupProfiles, level = 1)
        } else ""

        return buildString {
            appendLine("[MESSAGE_START]")
            appendLine("ID: ${message.conversationMessageId.value} | Отправитель: $author")
            appendLine("""Текст: "${message.messageText.value}""".trimIndent())
            if (forwardedBlock.isNotEmpty()) {
                appendLine(forwardedBlock)
            }
            append("[MESSAGE_END]")
        }
    }

    private fun formatForwardedBlock(
        forwarded: List<Message>,
        userProfiles: Map<FromId, VkUserProfile>,
        groupProfiles: Map<FromId, VkGroupProfile>,
        level: Int
    ): String {
        return forwarded.joinToString("\n") { msg ->
            val author = formatAuthor(msg.fromId, userProfiles, groupProfiles)
            val nested = if (msg.forwardedMessages.isNotEmpty()) {
                formatForwardedBlock(msg.forwardedMessages, userProfiles, groupProfiles, level + 1)
            } else ""

            val indent = "    ".repeat(level - 1)
            buildString {
                appendLine("${indent}[FORWARDED_BLOCK]")
                appendLine("${indent}Уровень: $level | Отправитель: $author")
                appendLine("""${indent}Текст: "${msg.messageText.value}""".trimIndent())
                if (nested.isNotEmpty()) {
                    appendLine(nested)
                }
                append("${indent}[END_LEVEL_$level]")
            }
        }
    }

    private fun formatAuthor(
        fromId: FromId,
        userProfiles: Map<FromId, VkUserProfile>,
        groupProfiles: Map<FromId, VkGroupProfile>
    ): String {
        return when {
            fromId.value < 0 -> {
                val profile = groupProfiles[fromId]
                profile?.let { "${it.name} (id${fromId.value})" } ?: "id${fromId.value}"
            }
            else -> {
                val profile = userProfiles[fromId]
                profile?.let { "${it.firstName} ${it.lastName} (id${fromId.value})" } ?: "id${fromId.value}"
            }
        }
    }

    private fun formatUserProfiles(profiles: Map<FromId, VkUserProfile>): String {
        if (profiles.isEmpty()) return ""
        return profiles.entries.joinToString("\n") { (id, profile) ->
            "- id${id.value}: ${profile.firstName} ${profile.lastName} (@${profile.screenName})"
        }
    }

    private fun formatGroupProfiles(profiles: Map<FromId, VkGroupProfile>): String {
        if (profiles.isEmpty()) return ""
        return profiles.entries.joinToString("\n") { (id, profile) ->
            "- id${id.value}: ${profile.name} (@${profile.screenName})"
        }
    }
}
