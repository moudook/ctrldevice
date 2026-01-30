package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver

/**
 * A simple tool to navigate to the Home screen.
 */
class HomeTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "go_home"
    override val description = "Navigates to the device home screen."

    override suspend fun execute(params: String): ToolResult {
        Log.d("HomeTool", "Executing Go Home command")

        val success = driver.home()

        return if (success) {
            ToolResult(success = true, output = "Navigated to Home Screen")
        } else {
            ToolResult(success = false, output = "Failed to perform HOME action")
        }
    }
}
