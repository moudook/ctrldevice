package com.ctrldevice.agent.tools

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ctrldevice.service.accessibility.ControllerService

/**
 * A tool that "reads" the current screen state by traversing the Accessibility Node hierarchy.
 * Returns a simplified text representation of what is visible.
 */
class ScreenReaderTool : AgentTool {
    override val name = "read_screen"
    override val description = "Reads the text content and structure of the current screen."

    override suspend fun execute(params: String): ToolResult {
        Log.d("ScreenReaderTool", "Executing Read Screen command")

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(
                success = false,
                output = "Accessibility Service not connected. Cannot read screen."
            )
        }

        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
             return ToolResult(
                success = false,
                output = "No active window content found."
            )
        }

        val screenContent = StringBuilder()
        traverseNode(rootNode, screenContent, 0)

        // Always recycle the root node to prevent leaks (though wrapper handles it usually, explicit is safer)
        // rootNode.recycle() // Warning: Don't recycle if we want to return it? No, we just built a string.

        return ToolResult(
            success = true,
            output = screenContent.toString()
        )
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null) return

        // Extract text or description
        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val viewId = node.viewIdResourceName

        if (!text.isNullOrBlank() || !contentDesc.isNullOrBlank()) {
            val indent = "  ".repeat(depth)

            // SECURITY: Redact passwords
            val label = if (node.isPassword) {
                "[REDACTED PASSWORD]"
            } else {
                text ?: contentDesc
            }

            val idStr = if (viewId != null) " [id: ${viewId.substringAfterLast("/")}]" else ""
            val clickable = if (node.isClickable) " (Clickable)" else ""

            sb.appendLine("$indent- $label$idStr$clickable")
        }

        // Traverse children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNode(child, sb, depth + 1)
            child?.recycle()
        }
    }
}
