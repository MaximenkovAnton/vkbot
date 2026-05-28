package com.simarel.vkbot.ai.command.vane

import com.simarel.vkbot.ai.port.output.vane.VaneSearchOutputPort
import com.simarel.vkbot.ai.port.output.vane.VaneSearchRequest
import com.simarel.vkbot.ai.port.output.vane.VaneSearchResponse
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import jakarta.enterprise.context.ApplicationScoped

interface VaneSearchCommand :
    Command<
            VaneSearchCommandRequest,
            VaneSearchCommandResponse,
            >

sealed class VaneSearchCommandRequest : CommandRequest {
    data class WebSearch(val query: String) : VaneSearchCommandRequest()
    data class AcademicSearch(val query: String) : VaneSearchCommandRequest()
    data class DiscussionSearch(val query: String) : VaneSearchCommandRequest()
}

@JvmInline
value class VaneSearchCommandResponse(val result: String) : CommandResponse

@ApplicationScoped
class VaneSearchCommandImpl(
    private val vaneSearchPort: VaneSearchOutputPort,
) : VaneSearchCommand {

    override fun execute(request: VaneSearchCommandRequest): VaneSearchCommandResponse {
        val result = when (request) {
            is VaneSearchCommandRequest.WebSearch -> executeSearch(
                query = request.query,
                sources = listOf("web"),
                optimizationMode = "balanced",
                sourceLabel = "Sources"
            )
            is VaneSearchCommandRequest.AcademicSearch -> executeSearch(
                query = request.query,
                sources = listOf("academic"),
                optimizationMode = "quality",
                sourceLabel = "Academic Sources"
            )
            is VaneSearchCommandRequest.DiscussionSearch -> executeSearch(
                query = request.query,
                sources = listOf("discussions"),
                optimizationMode = "balanced",
                sourceLabel = "Discussion Sources"
            )
        }
        return VaneSearchCommandResponse(result)
    }

    private fun executeSearch(query: String, sources: List<String>, optimizationMode: String, sourceLabel: String): String {
        return try {
            val response = vaneSearchPort.execute(
                VaneSearchRequest(
                    query = query,
                    sources = sources,
                    optimizationMode = optimizationMode,
                )
            )
            formatSearchResponse(response, sourceLabel)
        } catch (e: Exception) {
            "Search failed: ${e.message}"
        }
    }

    private fun formatSearchResponse(response: VaneSearchResponse, sourceLabel: String): String {
        return buildString {
            appendLine(response.message)
            if (response.sources.isNotEmpty()) {
                appendLine("\n--- $sourceLabel ---")
                response.sources.forEachIndexed { index, source ->
                    appendLine("[${index + 1}] ${source.title}: ${source.url}")
                }
            }
        }
    }
}
