package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver
import com.ctrldevice.agent.driver.UiNode

/**
 * A tool that "reads" the current screen state by traversing the Accessibility Node hierarchy.
 * Returns a simplified text representation of what is visible.
 */
class ScreenReaderTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "read_screen"
    override val description = "Reads the text content and structure of the current screen."

    override suspend fun execute(params: String): ToolResult {
        Log.d("ScreenReaderTool", "Executing Read Screen command")

        val rootNode = driver.getUiTree()
        if (rootNode == null) {
            return ToolResult(
                success = false,
                output = "No active window content found (or service not connected)."
            )
        }

        val screenContent = StringBuilder()
        traverseNode(rootNode, screenContent, 0)

        return ToolResult(
            success = true,
            output = screenContent.toString()
        )
    }

    private fun traverseNode(node: UiNode, sb: StringBuilder, depth: Int) {
        // Extract text or description
        val text = node.text
        val contentDesc = node.description
        val viewId = node.viewId

        if (!text.isNullOrBlank() || !contentDesc.isNullOrBlank()) {
            val indent = "  ".repeat(depth)

            // SECURITY: Redact passwords (if we had isPassword in UiNode, adding TODO)
            // For now, assuming standard text
            val label = text ?: contentDesc

            val idStr = if (viewId != null) " [id: ${viewId.substringAfterLast("/")}]" else ""
            val clickable = if (node.isClickable) " (Clickable)" else ""

            sb.appendLine("$indent- $label$idStr$clickable")
        }

        // Traverse children
        for (child in node.children) {
            traverseNode(child, sb, depth + 1)
        }
    }
}
