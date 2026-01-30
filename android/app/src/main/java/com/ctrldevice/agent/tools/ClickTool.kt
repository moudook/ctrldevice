package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver
import com.ctrldevice.agent.driver.ElementCriteria

/**
 * A tool that finds a UI element by text or ID and clicks it.
 */
class ClickTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "click_element"
    override val description = "Clicks a UI element matching the given text or View ID."

    override suspend fun execute(params: String): ToolResult {
        Log.d("ClickTool", "Executing Click command with params: $params")

        val target = params.trim()
        if (target.isEmpty()) {
            return ToolResult(false, "No target specified for click.")
        }

        // We try using the target as both ID and Text since we don't strictly know which it is
        val criteria = ElementCriteria(
            text = target,
            viewId = target, // Driver will try ID first, then Text
            matchSubstring = true
        )

        val success = driver.clickElement(criteria)

        return if (success) {
            ToolResult(true, "Clicked element matching '$target'")
        } else {
            ToolResult(false, "Could not find or click element matching '$target'")
        }
    }
}
