package com.ctrldevice.agent.tools

/**
 * Registry for all available tools.
 * Allows agents to discover and use tools dynamically.
 */
object ToolRegistry {
    private val tools = mutableMapOf<String, AgentTool>()

    init {
        // Register default system tools
        register(HomeTool())
        register(BackTool())
        register(OpenSettingsTool())
        register(BatteryTool())
        register(ScreenReaderTool())
        register(ClickTool())
        register(InputTextTool())
        register(ScrollTool())
        register(OpenChromeTool())
        register(LaunchAppTool())
        register(GestureTool())
    }

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AgentTool? {
        return tools[name]
    }

    fun getAllTools(): List<AgentTool> {
        return tools.values.toList()
    }

    /**
     * Simple semantic search (Mock LLM selection)
     * Finds the best tool based on the task description.
     */
    fun findToolForTask(description: String): AgentTool? {
        val lowerDesc = description.lowercase()

        // Explicit mapping based on keywords (Legacy logic moved here)
        return when {
            lowerDesc.contains("home") -> getTool("go_home")
            lowerDesc.contains("back") -> getTool("go_back")
            lowerDesc.contains("settings") -> getTool("open_settings")
            lowerDesc.contains("battery") -> getTool("check_battery")
            lowerDesc.contains("screen") || lowerDesc.contains("read") -> getTool("read_screen")
            lowerDesc.contains("click") -> getTool("click_element")
            lowerDesc.contains("type") -> getTool("input_text")
            lowerDesc.contains("scroll") -> getTool("scroll")
            lowerDesc.contains("search") || lowerDesc.contains("browse") || lowerDesc.contains("chrome") -> getTool("open_chrome")
            lowerDesc.contains("open") || lowerDesc.contains("launch") -> getTool("launch_app")
            lowerDesc.contains("swipe") || lowerDesc.contains("tap") || lowerDesc.contains("gesture") -> getTool("gesture")
            else -> null
        }
    }
}
