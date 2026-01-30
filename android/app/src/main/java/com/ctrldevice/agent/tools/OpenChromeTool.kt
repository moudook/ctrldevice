package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver

/**
 * A tool to open a URL in Chrome (or default browser).
 */
class OpenChromeTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "open_chrome"
    override val description = "Opens a URL in the Chrome browser."

    override suspend fun execute(params: String): ToolResult {
        Log.d("OpenChromeTool", "Executing Open Chrome command with params: $params")

        // Simple param parsing
        val url = if (params.startsWith("http")) params else "https://www.google.com/search?q=${params.replace(" ", "+")}"

        val success = driver.openUrl(url)

        return if (success) {
            ToolResult(success = true, output = "Opened Browser to: $url")
        } else {
            ToolResult(success = false, output = "Failed to open browser (check logs or service connection)")
        }
    }
}
