package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver

/**
 * A tool to open the system settings.
 */
class OpenSettingsTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "open_settings"
    override val description = "Opens the Android System Settings."

    override suspend fun execute(params: String): ToolResult {
        Log.d("OpenSettingsTool", "Executing Open Settings command")

        val success = driver.openSettings()

        return if (success) {
            ToolResult(success = true, output = "Launched System Settings")
        } else {
            ToolResult(success = false, output = "Failed to launch settings")
        }
    }
}
