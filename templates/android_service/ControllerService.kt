package com.ctrldevice.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * Template for the main Accessibility Service.
 * This is the "Hands" of the agent.
 */
class ControllerService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("CtrlDevice", "Service Connected")
        // Configure flags here if not done in XML
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 1. Capture Screen Content
        val rootNode = rootInActiveWindow
        // 2. Send to Agent Engine for analysis
    }

    override fun onMotionEvent(event: MotionEvent): Boolean {
        // SAFETY: Detect User Touch
        if (event.action == MotionEvent.ACTION_DOWN) {
            Log.d("CtrlDevice", "User touched screen! Pausing agent...")
            // TODO: Call AgentEngine.pause()
            return false // Let the touch pass through to the underlying app
        }
        return super.onMotionEvent(event)
    }

    override fun onInterrupt() {
        Log.w("CtrlDevice", "Service Interrupted")
    }
}
