package com.ctrldevice.agent.tools

import android.accessibilityservice.AccessibilityService
import com.ctrldevice.service.accessibility.ControllerService
import android.util.Log

/**
 * A simple tool to navigate to the Home screen.
 * This tests the agent's ability to control the device globally.
 */
class HomeTool : AgentTool {
    override val name = "go_home"
    override val description = "Navigates to the device home screen."

    override suspend fun execute(params: String): ToolResult {
        Log.d("HomeTool", "Executing Go Home command")

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(
                success = false,
                output = "Accessibility Service not connected. Please enable it in Settings."
            )
        }

        val success = service.performGlobalActionCommand(AccessibilityService.GLOBAL_ACTION_HOME)

        return if (success) {
            ToolResult(success = true, output = "Navigated to Home Screen")
        } else {
            ToolResult(success = false, output = "Failed to perform HOME action")
        }
    }
}
