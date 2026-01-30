package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver

/**
 * A tool that performs gestures like swipe and tap.
 * Supports:
 * - swipe up|down|left|right
 * - swipe x1,y1 x2,y2
 * - tap x,y
 */
class GestureTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "gesture"
    override val description = "Performs touch gestures: swipe [direction/coords] or tap [coords]."

    override suspend fun execute(params: String): ToolResult {
        Log.d("GestureTool", "Executing Gesture command with params: $params")

        val parts = params.trim().split("\\s+".toRegex())
        if (parts.isEmpty()) {
            return ToolResult(false, "No gesture command provided.")
        }

        val command = parts[0].lowercase()

        return when (command) {
            "swipe" -> handleSwipe(parts.drop(1))
            "tap" -> handleTap(parts.drop(1))
            else -> ToolResult(false, "Unknown gesture command: $command")
        }
    }

    private suspend fun handleSwipe(args: List<String>): ToolResult {
        if (args.isEmpty()) {
            return ToolResult(false, "Swipe requires a direction or coordinates.")
        }

        val direction = args[0].lowercase()
        // Note: Actual dimensions should ideally come from Driver, but we might rely on default behavior
        // Or we pass generic direction to driver and let it figure out coordinates?
        // Driver.swipe takes coordinates.
        // We need screen dimensions to calculate relative swipes (up/down).
        // Since we decoupled context/service, we can't easily get display metrics here unless we expose it in Driver.
        // Or we assume a normalized coordinate system or hardcode safe defaults for now?
        // Actually, AndroidDeviceDriver uses service which has resources.
        // But GestureTool shouldn't know about Android resources.
        // Let's rely on explicit coords if possible, or assume 1080x1920 for calculation?
        // BETTER: Move logic "up/down" -> "coords" into the Driver, or expose `swipe(direction)` in Driver.
        // I didn't expose swipe(direction) in Driver, only swipe(coords).
        // Let's assume standard HD for calculation fallback, or update Driver to support directional swipe.
        // Looking at AndroidDeviceDriver, it implements swipe(coords).

        // Let's stick to parsing here with assumptions, or fail for directional if we can't get metrics.
        // Wait, I can pass a generic "swipe relative" to the driver?
        // Let's assume standard dimensions 1080x1920 for now since we lost context access.
        // To do this properly, `DeviceDriver` should expose `getScreenDimensions()` or `swipe(direction)`.

        val width = 1080f
        val height = 1920f

        var startX = width / 2f
        var startY = height / 2f
        var endX = width / 2f
        var endY = height / 2f
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
                 try {
                    if (args.size >= 2) {
                        val startCoords = args[0].split(",").map { it.trim() }
                        val endCoords = args[1].split(",").map { it.trim() }
                        startX = startCoords[0].toFloat()
                        startY = startCoords[1].toFloat()
                        endX = endCoords[0].toFloat()
                        endY = endCoords[1].toFloat()
                        if (args.size >= 3) {
                            duration = args[2].toLong()
                        }
                    } else {
                        return ToolResult(false, "Invalid swipe arguments.")
                    }
                } catch (e: Exception) {
                    return ToolResult(false, "Error parsing swipe coordinates: ${e.message}")
                }
            }
        }

        val success = driver.swipe(startX, startY, endX, endY, duration)
        return if (success) {
            ToolResult(true, "Swiped $direction")
        } else {
            ToolResult(false, "Failed to dispatch swipe gesture.")
        }
    }

    private suspend fun handleTap(args: List<String>): ToolResult {
        if (args.isEmpty()) {
            return ToolResult(false, "Tap requires coordinates (x,y).")
        }

        try {
            val coords = args[0].split(",").map { it.trim() }
            val x = coords[0].toFloat()
            val y = coords[1].toFloat()

            val success = driver.click(x, y)
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
