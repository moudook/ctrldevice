package com.ctrldevice.agent.tools

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ctrldevice.service.accessibility.ControllerService
import kotlinx.coroutines.delay

/**
 * A tool that finds an editable UI element and types text into it.
 * Usage: "type [text]" (types into focused) or "type [text] into [target]"
 */
class InputTextTool : AgentTool {
    override val name = "input_text"
    override val description = "Types text into a text field. Can specify target or use currently focused element."

    override suspend fun execute(params: String): ToolResult {
        Log.d("InputTextTool", "Executing Input command with params: $params")

        // Simple parsing: split by "into" if present, otherwise treat whole string as text for focused element
        val parts = params.split(" into ")
        val textToType = parts[0].trim()
        val target = if (parts.size > 1) parts[1].trim() else null

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(false, "Accessibility Service not connected.")
        }

        val rootNode = service.rootInActiveWindow ?: return ToolResult(false, "No active window found.")

        var nodeToTypeIn: AccessibilityNodeInfo? = null

        if (target != null) {
            // 1. Find specific target
             nodeToTypeIn = findNodeByTextRecursive(rootNode, target)
             if (nodeToTypeIn == null) {
                 // Try ID
                 val nodes = rootNode.findAccessibilityNodeInfosByViewId(target)
                 if (!nodes.isNullOrEmpty()) {
                     nodeToTypeIn = nodes[0]
                 }
             }
        } else {
            // 2. Find currently focused element
            nodeToTypeIn = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (nodeToTypeIn == null) {
                // Fallback: find the first editable element
                nodeToTypeIn = findEditableNodeRecursive(rootNode)
            }
        }

        if (nodeToTypeIn == null) {
            val msg = if (target != null) "Could not find element '$target'" else "Could not find any focused or editable element"
            return ToolResult(false, msg)
        }

        // AUTO-FOCUS LOGIC
        if (!nodeToTypeIn.isFocused) {
            Log.d("InputTextTool", "Target not focused. Attempting to focus...")
            var focused = nodeToTypeIn.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (!focused) {
                // Fallback to click if focus action fails (common in some custom views)
                focused = nodeToTypeIn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }

            if (focused) {
                delay(500) // Give UI time to react (keyboard popup, etc)
            }
        }

        // Perform the action
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)

        val success = nodeToTypeIn.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        return if (success) {
            ToolResult(true, "Typed '$textToType'")
        } else {
            ToolResult(false, "Found element but failed to set text. (Is it editable?)")
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
        }
        return null
    }

    private fun findEditableNodeRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNodeRecursive(child)
            if (result != null) return result
        }
        return null
    }
}
