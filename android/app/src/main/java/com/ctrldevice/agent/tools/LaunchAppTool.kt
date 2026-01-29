package com.ctrldevice.agent.tools

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.ctrldevice.service.accessibility.ControllerService

/**
 * A tool that launches an application by name or package name.
 */
class LaunchAppTool : AgentTool {
    override val name = "launch_app"
    override val description = "Launches an installed application. Usage: 'launch_app [App Name]'"

    override suspend fun execute(params: String): ToolResult {
        Log.d("LaunchAppTool", "Executing Launch App command with params: $params")

        val query = params.trim()
        if (query.isEmpty()) {
            return ToolResult(false, "No app name specified.")
        }

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(false, "Accessibility Service not connected.")
        }

        val pm = service.packageManager

        // 1. Try exact package match first
        var launchIntent = pm.getLaunchIntentForPackage(query)

        // 2. If not found, search by App Name (Label)
        if (launchIntent == null) {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            var bestMatchPackage: String? = null

            // Simple case-insensitive search
            for (appInfo in installedApps) {
                val label = pm.getApplicationLabel(appInfo).toString()
                if (label.equals(query, ignoreCase = true)) {
                    bestMatchPackage = appInfo.packageName
                    break
                }
                // Partial match fallback
                if (label.contains(query, ignoreCase = true)) {
                    if (bestMatchPackage == null) {
                        bestMatchPackage = appInfo.packageName
                    }
                }
            }

            if (bestMatchPackage != null) {
                launchIntent = pm.getLaunchIntentForPackage(bestMatchPackage)
            }
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(launchIntent)
            return ToolResult(true, "Launched app matching '$query'")
        } else {
            return ToolResult(false, "Could not find installed app matching '$query'")
        }
    }
}
