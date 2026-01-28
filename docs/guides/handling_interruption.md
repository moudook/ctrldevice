# Safety: Handling User Interruption

## The Rule
**"User Input is Supreme."**
If the user touches the screen, presses a button, or interacts with the device in any way, the Agent MUST pause immediately.

## Implementation Details

### 1. Intercepting Touch
The `AccessibilityService` is the only component capable of seeing global touch events reliably without rooting.

**File:** `com.ctrldevice.service.accessibility.MyAccessibilityService`

```kotlin
override fun onMotionEvent(event: MotionEvent): Boolean {
    if (event.action == MotionEvent.ACTION_DOWN) {
        // 1. Notify the Engine to PAUSE
        AgentEngine.getInstance().pause()
        
        // 2. Visual Feedback
        OverlayService.showStatus("Paused by User")
        
        // 3. Do NOT consume the event. Let the user's click go through.
        return false 
    }
    return super.onMotionEvent(event)
}
```

### 2. Resume Logic
After a pause, the agent should not auto-resume immediately. It should:
1.  Show a "Resume" button on the Overlay.
2.  Or wait for a voice command "Continue".

### 3. Edge Cases
*   **Incoming Call:** The agent should pause on `PHONE_STATE_RINGING`.
*   **Screen Off:** The agent should pause if the screen turns off, unless explicitly configured to run in background (e.g., music playback).
