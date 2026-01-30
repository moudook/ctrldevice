package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver
import com.ctrldevice.agent.driver.ElementCriteria

/**
 * A tool that finds an editable UI element and types text into it.
 * Usage: "type [text]" (types into focused) or "type [text] into [target]"
 */
class InputTextTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "input_text"
    override val description = "Types text into a text field. Can specify target or use currently focused element."

    override suspend fun execute(params: String): ToolResult {
        Log.d("InputTextTool", "Executing Input command with params: $params")

        // Simple parsing: split by "into" if present, otherwise treat whole string as text for focused element
        val parts = params.split(" into ")
        val textToType = parts[0].trim()
        val target = if (parts.size > 1) parts[1].trim() else null

        val criteria = if (target != null) {
            ElementCriteria(text = target, viewId = target, matchSubstring = true)
        } else {
            // Empty criteria implies "find focused or first editable" in our Driver implementation
            ElementCriteria()
        }

        val success = driver.inputText(criteria, textToType)

        return if (success) {
            ToolResult(true, "Typed '$textToType'")
        } else {
            ToolResult(false, "Failed to type text. Target editable field not found.")
        }
    }
}
