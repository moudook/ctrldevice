package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver
import com.ctrldevice.agent.driver.ElementCriteria
import com.ctrldevice.agent.driver.ScrollDirection

/**
 * A tool that finds a scrollable UI element and performs a scroll action.
 * Usage: "scroll down", "scroll up", "scroll [target]"
 */
class ScrollTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "scroll"
    override val description = "Scrolls the screen or a specific element. Directions: forward (down) or backward (up)."

    override suspend fun execute(params: String): ToolResult {
        Log.d("ScrollTool", "Executing Scroll command with params: $params")

        val direction = if (params.contains("up", ignoreCase = true) || params.contains("back", ignoreCase = true)) {
            ScrollDirection.BACKWARD
        } else {
            ScrollDirection.FORWARD
        }

        // Logic: if params contains more than just "up/down/scroll", treat rest as target
        // e.g. "scroll list" -> target="list"
        val cleanParams = params.replace("scroll", "", ignoreCase = true)
            .replace("down", "", ignoreCase = true)
            .replace("up", "", ignoreCase = true)
            .replace("forward", "", ignoreCase = true)
            .replace("backward", "", ignoreCase = true)
            .trim()

        val criteria = if (cleanParams.isNotEmpty()) {
            ElementCriteria(text = cleanParams, viewId = cleanParams, matchSubstring = true)
        } else {
            ElementCriteria() // Any scrollable
        }

        val success = driver.scroll(criteria, direction)

        val dirString = if (direction == ScrollDirection.FORWARD) "down/forward" else "up/backward"

        return if (success) {
            ToolResult(true, "Scrolled $dirString")
        } else {
            ToolResult(false, "Failed to scroll $dirString (Found scrollable element? ${if (cleanParams.isNotEmpty()) "Target: $cleanParams" else "Any"})")
        }
    }
}
