package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.service.accessibility.ControllerService

/**
 * A tool that performs gestures like swipe and tap.
 * Supports:
 * - swipe up|down|left|right
 * - swipe x1,y1 x2,y2
 * - tap x,y
 */
class GestureTool : AgentTool {
    override val name = "gesture"
    override val description = "Performs touch gestures: swipe [direction/coords] or tap [coords]."

    override suspend fun execute(params: String): ToolResult {
        Log.d("GestureTool", "Executing Gesture command with params: $params")

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(false, "Accessibility Service not connected.")
        }

        val parts = params.trim().split("\\s+".toRegex())
        if (parts.isEmpty()) {
            return ToolResult(false, "No gesture command provided.")
        }

        val command = parts[0].lowercase()

        return when (command) {
            "swipe" -> handleSwipe(parts.drop(1), service)
            "tap" -> handleTap(parts.drop(1), service)
            else -> ToolResult(false, "Unknown gesture command: $command")
        }
    }

    private fun handleSwipe(args: List<String>, service: ControllerService): ToolResult {
        if (args.isEmpty()) {
            return ToolResult(false, "Swipe requires a direction or coordinates.")
        }

        // Check if it's a direction
        val direction = args[0].lowercase()
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        val centerX = width / 2f
        val centerY = height / 2f

        var startX = centerX
        var startY = centerY
        var endX = centerX
        var endY = centerY
        var duration = 500L

        when (direction) {
            "up" -> {
                startY = height * 0.8f
                endY = height * 0.2f
            }
            "down" -> {
                startY = height * 0.2f
                endY = height * 0.8f
            }
            "left" -> {
                startX = width * 0.8f
                endX = width * 0.2f
            }
            "right" -> {
                startX = width * 0.2f
                endX = width * 0.8f
            }
            else -> {
                // Try parsing coordinates: x1,y1 x2,y2
                try {
                    if (args.size >= 2) {
                        val startCoords = args[0].split(",")
                        val endCoords = args[1].split(",")
                        startX = startCoords[0].toFloat()
                        startY = startCoords[1].toFloat()
                        endX = endCoords[0].toFloat()
                        endY = endCoords[1].toFloat()
                        if (args.size >= 3) {
                            duration = args[2].toLong()
                        }
                    } else {
                        return ToolResult(false, "Invalid swipe arguments. Use 'swipe [up|down|left|right]' or 'swipe x1,y1 x2,y2 [duration]'.")
                    }
                } catch (e: Exception) {
                    return ToolResult(false, "Error parsing swipe coordinates: ${e.message}")
                }
            }
        }

        val success = service.dispatchSwipe(startX, startY, endX, endY, duration)
        return if (success) {
            ToolResult(true, "Swiped $direction ($startX,$startY to $endX,$endY)")
        } else {
            ToolResult(false, "Failed to dispatch swipe gesture.")
        }
    }

    private fun handleTap(args: List<String>, service: ControllerService): ToolResult {
        if (args.isEmpty()) {
            return ToolResult(false, "Tap requires coordinates (x,y).")
        }

        try {
            val coords = args[0].split(",")
            val x = coords[0].toFloat()
            val y = coords[1].toFloat()

            val success = service.dispatchTap(x, y)
            return if (success) {
                ToolResult(true, "Tapped at $x, $y")
            } else {
                ToolResult(false, "Failed to dispatch tap gesture.")
            }
        } catch (e: Exception) {
            return ToolResult(false, "Error parsing tap coordinates: ${e.message}")
        }
    }
}
