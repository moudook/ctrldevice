package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver

/**
 * A tool that launches an application by name or package name.
 */
class LaunchAppTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "launch_app"
    override val description = "Launches an installed application. Usage: 'launch_app [App Name]'"

    override suspend fun execute(params: String): ToolResult {
        Log.d("LaunchAppTool", "Executing Launch App command with params: $params")

        val query = params.trim()
        if (query.isEmpty()) {
            return ToolResult(false, "No app name specified.")
        }

        val success = driver.launchAppSearch(query)

        return if (success) {
            ToolResult(true, "Launched app matching '$query'")
        } else {
            ToolResult(false, "Could not find installed app matching '$query'")
        }
    }
}
