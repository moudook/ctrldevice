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
 * The "Eyes" and "Touch" of the system.
 * Feeds raw data into the Proprioception stream.
 */
class ControllerService : AccessibilityService() {

    companion object {
        // Event Stream for the Agent Brain
        val screenEvents = MutableSharedFlow<AccessibilityEvent>(replay = 1)
        val userInterrupts = MutableSharedFlow<Boolean>()

        // Simple singleton instance for the prototype to access service methods directly
        var instance: ControllerService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("CtrlDevice", "ControllerService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            // Stream to Proprioception System
             screenEvents.tryEmit(it)
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
}
