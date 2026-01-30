package com.ctrldevice.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Safe immutable copy of relevant AccessibilityEvent data.
 * Essential because the system recycles AccessibilityEvent objects immediately.
 */
data class SafeAccessibilityEvent(
    val eventType: Int,
    val packageName: String?,
    val className: String?,
    val text: List<String>,
    val contentDescription: String?,
    val isScrollable: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * The "Eyes" and "Touch" of the system.
 * Feeds raw data into the Proprioception stream.
 */
class ControllerService : AccessibilityService() {

    companion object {
        // Event Stream for the Agent Brain
        // Optimization: Use safe data object to prevent use-after-recycle bugs
        val screenEvents = MutableSharedFlow<SafeAccessibilityEvent>(replay = 1)
        val userInterrupts = MutableSharedFlow<Boolean>()

        // Simple singleton instance for the prototype to access service methods directly
        var instance: ControllerService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("CtrlDevice", "ControllerService connected")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Q46: Auto-Rotate Support
        // We log this change. The agent effectively "adapts" because the next
        // read_screen() call will return coordinates mapped to the new rotation.
        Log.d("CtrlDevice", "Configuration changed: Orientation=${newConfig.orientation}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            // Stream to Proprioception System
            // CRITICAL FIX: Map to safe object immediately. 'event' is recycled by system after return.
            val safeEvent = SafeAccessibilityEvent(
                eventType = it.eventType,
                packageName = it.packageName?.toString(),
                className = it.className?.toString(),
                text = it.text.map { txt -> txt.toString() }, // Deep copy text list
                contentDescription = it.contentDescription?.toString(),
                isScrollable = it.isScrollable
            )
            screenEvents.tryEmit(safeEvent)
        }
    }

    override fun onMotionEvent(event: MotionEvent) {
        // SAFETY: Detect User Touch
        if (event.action == MotionEvent.ACTION_DOWN) {
            Log.d("CtrlDevice", "User touched screen! Pausing agent...")
            userInterrupts.tryEmit(true)
            // We don't return a value here as the base method returns Unit
        }
        super.onMotionEvent(event)
    }

    override fun onInterrupt() {
        Log.w("CtrlDevice", "Service Interrupted")
        instance = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /**
     * Executes a global action (Bare minimum capability)
     */
    fun performGlobalActionCommand(action: Int): Boolean {
        return performGlobalAction(action)
    }

    /**
     * Dispatches a swipe gesture.
     */
    fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)

        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, null, null)
    }

    /**
     * Dispatches a tap gesture.
     */
    fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)

        // A tap is a stroke with a very short duration at a single point
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, null, null)
    }

    /**
     * Takes a screenshot and returns the bitmap via a callback.
     * Requires API 30+ (Android 11)
     */
    fun captureScreenshot(onResult: (android.graphics.Bitmap?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                this.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            // Copy to software bitmap to be accessible/mutable
                            val softwareBitmap = bitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            screenshot.hardwareBuffer.close()
                            onResult(softwareBitmap)
                        } catch (e: Exception) {
                            Log.e("ControllerService", "Error processing screenshot: ${e.message}")
                            onResult(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e("ControllerService", "Screenshot failed with error code: $errorCode")
                        onResult(null)
                    }
                }
            )
        } else {
            Log.e("ControllerService", "Screenshot not supported (Requires Android 11+)")
            onResult(null)
        }
    }
}
