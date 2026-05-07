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

    @Tool("Use the tool for generic web search. Returns a comprehensive answer with sources.")
    fun search_web(query: String): String {
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

    @Tool("Use the tool for academic web search. Best for research and scientific queries.")
    fun search_academic(query: String): String {
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

    @Tool("Use the tool for discussions web search. Best for opinions and community insights.")
    fun search_discussions(query: String): String {
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
            is VaneSearchCommandRequest.WebSearch -> search_web(request.query)
            is VaneSearchCommandRequest.AcademicSearch -> search_academic(request.query)
            is VaneSearchCommandRequest.DiscussionSearch -> search_discussions(request.query)
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
