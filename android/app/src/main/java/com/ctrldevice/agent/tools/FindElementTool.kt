package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver
import com.ctrldevice.agent.driver.ElementCriteria

/**
 * A tool that searches for a UI element and returns its details (bounds, clickable, etc.).
 */
class FindElementTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "find_element"
    override val description = "Finds a UI element by text or ID and returns its details (bounds, clickable, etc.)."

    override suspend fun execute(params: String): ToolResult {
        Log.d("FindElementTool", "Executing Find Element with params: $params")

        val target = params.trim()
        if (target.isEmpty()) {
            return ToolResult(false, "No search query specified.")
        }

        val criteria = ElementCriteria(text = target, viewId = target, matchSubstring = true)
        val element = driver.findElement(criteria)

        return if (element != null) {
            val info = buildString {
                append("Found element: '${element.text ?: "No Text"}'\n")
                append("Bounds: ${element.bounds} (Center: ${element.bounds.centerX()}, ${element.bounds.centerY()})\n")
                append("Clickable: ${element.isClickable}\n")
                append("Enabled: true\n") // Simplified in UiElement for now
                append("Scrollable: ${element.isScrollable}\n")
            }
            ToolResult(true, info)
        } else {
            ToolResult(false, "Element '$target' not found on screen.")
        }
    }
}
