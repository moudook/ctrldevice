package com.ctrldevice.agent.tools

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.ctrldevice.service.accessibility.ControllerService

/**
 * A tool to perform the global Back action.
 */
class BackTool : AgentTool {
    override val name = "go_back"
    override val description = "Presses the Back button."

    override suspend fun execute(params: String): ToolResult {
        Log.d("BackTool", "Executing Back command")

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(false, "Accessibility Service not connected.")
        }

        val success = service.performGlobalActionCommand(AccessibilityService.GLOBAL_ACTION_BACK)

        return if (success) {
            ToolResult(true, "Pressed Back")
        } else {
            ToolResult(false, "Failed to perform BACK action")
        }
    }
}
