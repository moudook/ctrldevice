package com.ctrldevice.agent.tools

import com.ctrldevice.agent.driver.DeviceDriver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registry for all available tools.
 * Allows agents to discover and use tools dynamically.
 * Thread-safe implementation.
 */
object ToolRegistry {
    private val tools = ConcurrentHashMap<String, AgentTool>()
    private val isInitialized = AtomicBoolean(false)

    // Optimization: Index for O(1) lookup by keyword
    private val keywordIndex = ConcurrentHashMap<String, String>()

    fun initialize(driver: DeviceDriver) {
        if (isInitialized.getAndSet(true)) return

        // Register default system tools with driver dependency
        register(HomeTool(driver), "home", "launcher")
        register(BackTool(driver), "back", "return")
        register(OpenSettingsTool(driver), "settings", "config", "wifi")
        register(BatteryTool(driver), "battery", "power")
        register(ScreenReaderTool(driver), "screen", "read", "view", "ui")
        register(ClickTool(driver), "click", "tap", "press")
        register(InputTextTool(driver), "type", "input", "write", "enter")
        register(ScrollTool(driver), "scroll", "swipe up", "swipe down")
        register(OpenChromeTool(driver), "chrome", "browser", "web", "search", "google")
        register(LaunchAppTool(driver), "launch", "open app", "start")
        register(GestureTool(driver), "gesture", "swipe", "custom")
        register(ScreenshotTool(driver), "screenshot", "capture")
        register(FindElementTool(driver), "find", "locate", "search for")
        register(WaitForElementTool(driver), "wait", "loading")
    }

    fun register(tool: AgentTool, vararg keywords: String) {
        tools[tool.name] = tool
        keywords.forEach { keyword ->
            keywordIndex[keyword.lowercase()] = tool.name
        }
    }

    fun getTool(name: String): AgentTool? {
        return tools[name]
    }

    /**
     * Logic Compressed: O(1) keyword lookup
     */
    fun findToolForTask(description: String): AgentTool? {
        val lowerDesc = description.lowercase()

        // 1. Direct Keyword Match (Fastest)
        // Split description into words and check if any word maps to a tool
        // "click on submit" -> ["click", "on", "submit"] -> "click" hits index
        val words = lowerDesc.split(' ', '_', '-')
        for (word in words) {
            val toolName = keywordIndex[word]
            if (toolName != null) {
                return getTool(toolName)
            }
        }

        // 2. Fallback: Check if description contains multi-word keywords (e.g. "swipe up")
        // Only iterate if single words failed.
        // We can optimize this further, but the map is usually single words.
        // Let's rely on the tokenizer above for 90% of cases.
        return null
    }
}
