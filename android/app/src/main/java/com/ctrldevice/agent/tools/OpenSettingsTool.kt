package com.ctrldevice.agent.tools

import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.ctrldevice.service.accessibility.ControllerService

/**
 * A tool to open the system settings.
 */
class OpenSettingsTool : AgentTool {
    override val name = "open_settings"
    override val description = "Opens the Android System Settings."

    override suspend fun execute(params: String): ToolResult {
        Log.d("OpenSettingsTool", "Executing Open Settings command")

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(
                success = false,
                output = "Accessibility Service not connected. Cannot launch activity."
            )
        }

        return try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)

            ToolResult(success = true, output = "Launched System Settings")
        } catch (e: Exception) {
            ToolResult(success = false, output = "Failed to launch settings: ${e.message}")
        }
    }
}
