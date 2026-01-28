package com.ctrldevice.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The "Eyes" and "Touch" of the system.
 * Feeds raw data into the Proprioception stream.
 */
class ControllerService : AccessibilityService() {

    companion object {
        // Event Stream for the Agent Brain
        val screenEvents = MutableSharedFlow<AccessibilityEvent>()
        val userInterrupts = MutableSharedFlow<Boolean>()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            // Stream to Proprioception System
            // In real app: map to a simplified data class
             screenEvents.tryEmit(it)
        }
    }

    override fun onMotionEvent(event: MotionEvent): Boolean {
        // SAFETY: Detect User Touch
        if (event.action == MotionEvent.ACTION_DOWN) {
            Log.d("CtrlDevice", "User touched screen! Pausing agent...")
            userInterrupts.tryEmit(true)
            return false 
        }
        return super.onMotionEvent(event)
    }

    override fun onInterrupt() {
        Log.w("CtrlDevice", "Service Interrupted")
    }
}