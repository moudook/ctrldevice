package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver
import com.ctrldevice.agent.driver.ElementCriteria

/**
 * A tool that waits for a UI element to appear on the screen.
 */
class WaitForElementTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "wait_for_element"
    override val description = "Waits for an element (text or ID) to appear within a timeout (default 10s)."

    override suspend fun execute(params: String): ToolResult {
        // Params format: "target_text" or "target_text|timeout_seconds"
        val parts = params.split("|")
        val target = parts[0].trim()
        val timeoutSeconds = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 10L

        Log.d("WaitForElementTool", "Waiting for '$target' (Timeout: ${timeoutSeconds}s)")

        if (target.isEmpty()) {
            return ToolResult(false, "No target specified.")
        }

        val criteria = ElementCriteria(text = target, viewId = target, matchSubstring = true)
        val appeared = driver.waitForElement(criteria, timeoutSeconds * 1000)

        return if (appeared) {
            ToolResult(true, "Element '$target' appeared.")
        } else {
            ToolResult(false, "Timeout: Element '$target' did not appear within ${timeoutSeconds}s.")
        }
    }
}
