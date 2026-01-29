package com.ctrldevice.agent.tools

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.ctrldevice.service.accessibility.ControllerService

/**
 * A tool to open a URL in Chrome (or default browser).
 */
class OpenChromeTool : AgentTool {
    override val name = "open_chrome"
    override val description = "Opens a URL in the Chrome browser."

    override suspend fun execute(params: String): ToolResult {
        Log.d("OpenChromeTool", "Executing Open Chrome command with params: $params")

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(
                success = false,
                output = "Accessibility Service not connected. Cannot launch activity."
            )
        }

        // Simple param parsing (assuming params is just the URL string for this prototype)
        // In a real version, we'd parse JSON.
        val url = if (params.startsWith("http")) params else "https://www.google.com/search?q=${params.replace(" ", "+")}"

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // intent.setPackage("com.android.chrome") // Optional: force Chrome
            service.startActivity(intent)

            ToolResult(success = true, output = "Opened Browser to: $url")
        } catch (e: Exception) {
            ToolResult(success = false, output = "Failed to open browser: ${e.message}")
        }
    }
}
