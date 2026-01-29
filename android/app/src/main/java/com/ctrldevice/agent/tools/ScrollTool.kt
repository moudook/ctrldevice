package com.ctrldevice.agent.tools

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ctrldevice.service.accessibility.ControllerService

/**
 * A tool that finds a scrollable UI element and performs a scroll action.
 * Usage: "scroll down", "scroll up", "scroll [target]"
 */
class ScrollTool : AgentTool {
    override val name = "scroll"
    override val description = "Scrolls the screen or a specific element. Directions: forward (down) or backward (up)."

    override suspend fun execute(params: String): ToolResult {
        Log.d("ScrollTool", "Executing Scroll command with params: $params")

        val direction = if (params.contains("up", ignoreCase = true) || params.contains("back", ignoreCase = true)) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD // Default to scrolling down/forward
        }

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(false, "Accessibility Service not connected.")
        }

        val rootNode = service.rootInActiveWindow ?: return ToolResult(false, "No active window found.")

        // 1. Find a scrollable node
        val scrollableNode = findScrollableNodeRecursive(rootNode)

        if (scrollableNode == null) {
            return ToolResult(false, "Could not find any scrollable element on screen.")
        }

        // Perform the action
        val success = scrollableNode.performAction(direction)

        val dirString = if (direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) "down/forward" else "up/backward"

        return if (success) {
            ToolResult(true, "Scrolled $dirString")
        } else {
            ToolResult(false, "Found scrollable element but failed to scroll $dirString.")
        }
    }

    private fun findScrollableNodeRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNodeRecursive(child)
            if (result != null) return result
        }
        return null
    }
}
