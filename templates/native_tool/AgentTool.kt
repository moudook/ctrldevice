package com.ctrldevice.agent.tools

/**
 * Base interface for all tools (skills) the agent can use.
 * Examples: GmailTool, YouTubeTool, ChromeTool.
 */
interface AgentTool {
    
    val name: String
    val description: String

    /**
     * Returns the schema of this tool for the LLM.
     * Format: JSON Schema or Function Calling definition.
     */
    fun getSchema(): String

    /**
     * Executes the tool action.
     * @param params JSON arguments provided by the LLM.
     */
    suspend fun execute(params: String): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val output: String, // Information extracted or confirmation
    val needsScreenshot: Boolean = true
)
