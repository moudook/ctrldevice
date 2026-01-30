package com.ctrldevice.agent.tools

import android.graphics.Bitmap
import android.util.Base64
import com.ctrldevice.agent.driver.DeviceDriver
import java.io.ByteArrayOutputStream

/**
 * tool that captures the current screen state as a Base64 encoded JPEG.
 */
class ScreenshotTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "take_screenshot"
    override val description = "Captures the screen and returns a Base64 encoded image."

    override suspend fun execute(params: String): ToolResult {
        val bitmap = driver.takeScreenshot()

        return if (bitmap != null) {
            try {
                val base64Image = bitmapToBase64(bitmap)
                bitmap.recycle() // Recycle local copy
                ToolResult(true, base64Image)
            } catch (e: Exception) {
                ToolResult(false, "Failed to encode screenshot: ${e.message}")
            }
        } else {
            ToolResult(false, "Screenshot capture returned null (Screen might be protected or API < 30).")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Optimization: Downscale if too large to save tokens/bandwidth
        val maxDimension = 1080
        val scale = if (bitmap.height > maxDimension || bitmap.width > maxDimension) {
            val ratio = maxDimension.toFloat() / kotlin.math.max(bitmap.height, bitmap.width)
            ratio
        } else {
            1.0f
        }

        val processedBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val byteArrayOutputStream = ByteArrayOutputStream()
        // Compress to JPEG, Quality 80 (Good balance)
        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)

        // Recycle the scaled bitmap if it's a new instance
        if (processedBitmap !== bitmap) {
            processedBitmap.recycle()
        }

        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
