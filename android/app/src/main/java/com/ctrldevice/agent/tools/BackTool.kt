package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver

/**
 * A tool to perform the global Back action.
 */
class BackTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "go_back"
    override val description = "Presses the Back button."

    override suspend fun execute(params: String): ToolResult {
        Log.d("BackTool", "Executing Back command")

        val success = driver.back()

        return if (success) {
            ToolResult(true, "Pressed Back")
        } else {
            ToolResult(false, "Failed to perform BACK action")
        }
    }
}
