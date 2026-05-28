package com.simarel.vkbot.ai.adapter.output.ai.tool

import com.simarel.vkbot.ai.command.vane.VaneSearchCommand
import com.simarel.vkbot.ai.command.vane.VaneSearchCommandRequest
import dev.langchain4j.agent.tool.Tool
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class VaneSearchToolAdapter(
    private val vaneSearchCommand: VaneSearchCommand,
) {

    @Tool("Use the tool for generic web search. Returns a comprehensive answer with sources.")
    fun search_web(query: String): String {
        val response = vaneSearchCommand.execute(VaneSearchCommandRequest.WebSearch(query))
        return response.result
    }

    @Tool("Use the tool for academic web search. Best for research and scientific queries.")
    fun search_academic(query: String): String {
        val response = vaneSearchCommand.execute(VaneSearchCommandRequest.AcademicSearch(query))
        return response.result
    }

    @Tool("Use the tool for discussions web search. Best for opinions and community insights.")
    fun search_discussions(query: String): String {
        val response = vaneSearchCommand.execute(VaneSearchCommandRequest.DiscussionSearch(query))
        return response.result
    }
}
