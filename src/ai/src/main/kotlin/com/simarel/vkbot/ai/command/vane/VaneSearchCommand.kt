package com.simarel.vkbot.ai.command.vane

import com.simarel.vkbot.ai.port.output.vane.VaneSearchOutputPort
import com.simarel.vkbot.ai.port.output.vane.VaneSearchRequest
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import dev.langchain4j.agent.tool.Tool
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

    @Tool("Searches the web using Vane (Perplexica) AI search engine. Returns a comprehensive answer with sources.")
    fun searchWeb(query: String): String {
        return try {
            val response = vaneSearchPort.execute(
                VaneSearchRequest(
                    query = query,
                    sources = listOf("web"),
                    optimizationMode = "balanced",
                )
            )
            formatSearchResponse(response, "Sources")
        } catch (e: Exception) {
            "Search failed: ${e.message}"
        }
    }

    @Tool("Searches academic sources using Vane (Perplexica) AI search engine. Best for research and scientific queries.")
    fun searchAcademic(query: String): String {
        return try {
            val response = vaneSearchPort.execute(
                VaneSearchRequest(
                    query = query,
                    sources = listOf("academic"),
                    optimizationMode = "quality",
                )
            )
            formatSearchResponse(response, "Academic Sources")
        } catch (e: Exception) {
            "Academic search failed: ${e.message}"
        }
    }

    @Tool("Searches discussions and forums using Vane (Perplexica) AI search engine. Best for opinions and community insights.")
    fun searchDiscussions(query: String): String {
        return try {
            val response = vaneSearchPort.execute(
                VaneSearchRequest(
                    query = query,
                    sources = listOf("discussions"),
                    optimizationMode = "balanced",
                )
            )
            formatSearchResponse(response, "Discussion Sources")
        } catch (e: Exception) {
            "Discussion search failed: ${e.message}"
        }
    }

    override fun execute(request: VaneSearchCommandRequest): VaneSearchCommandResponse {
        val result = when (request) {
            is VaneSearchCommandRequest.WebSearch -> searchWeb(request.query)
            is VaneSearchCommandRequest.AcademicSearch -> searchAcademic(request.query)
            is VaneSearchCommandRequest.DiscussionSearch -> searchDiscussions(request.query)
        }
        return VaneSearchCommandResponse(result)
    }

    private fun formatSearchResponse(response: com.simarel.vkbot.ai.port.output.vane.VaneSearchResponse, sourceLabel: String): String {
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
