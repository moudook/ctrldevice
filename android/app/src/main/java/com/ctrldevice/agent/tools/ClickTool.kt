package com.ctrldevice.agent.tools

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ctrldevice.service.accessibility.ControllerService

/**
 * A tool that finds a UI element by text or ID and clicks it.
 */
class ClickTool : AgentTool {
    override val name = "click_element"
    override val description = "Clicks a UI element matching the given text or View ID."

    override suspend fun execute(params: String): ToolResult {
        Log.d("ClickTool", "Executing Click command with params: $params")

        val target = params.trim()
        if (target.isEmpty()) {
            return ToolResult(false, "No target specified for click.")
        }

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(false, "Accessibility Service not connected.")
        }

        val rootNode = service.rootInActiveWindow ?: return ToolResult(false, "No active window found.")

        // 1. Try finding by text (exact match first, then contains)
        var nodes = rootNode.findAccessibilityNodeInfosByText(target)

        // 2. If not found, try searching manually (findAccessibilityNodeInfosByText is sometimes limited)
        if (nodes.isNullOrEmpty()) {
             val foundNode = findNodeByTextRecursive(rootNode, target)
             if (foundNode != null) {
                 nodes = listOf(foundNode)
             }
        }

        // 3. Try finding by View ID (if target looks like an ID)
        if (nodes.isNullOrEmpty()) {
            // IDs are usually fully qualified like "com.package:id/button_name"
            // But we might search by just "button_name" if we implemented smarter search.
            // For now, rely on standard API.
            nodes = rootNode.findAccessibilityNodeInfosByViewId(target)
        }

        if (nodes.isNullOrEmpty()) {
            return ToolResult(false, "Could not find element matching '$target'")
        }

        // 4. Click the first clickable ancestor
        // Sometimes the text is on a TextView inside a clickable Layout
        val nodeToClick = nodes[0]
        val clickableNode = findClickableAncestor(nodeToClick)

        return if (clickableNode != null) {
            val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // Recycle *other* nodes if we had a list? The API returns new instances.
            // In a real app we should be careful with recycling.
            // For this prototype, we rely on GC eventually or simple usage.

            if (success) {
                ToolResult(true, "Clicked element containing '$target'")
            } else {
                ToolResult(false, "Found '$target' but failed to perform CLICK action.")
            }
        } else {
            ToolResult(false, "Element '$target' found but it (and its parents) are not clickable.")
        }
    }

    private fun findNodeByTextRecursive(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(target, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(target, ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextRecursive(child, target)
            if (result != null) return result
            // child.recycle() // Be careful not to recycle if returning it!
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }
        return null
    }
}
