# Robust Agent Logic & Pseudocode

## 1. The Core Agent Loop (Main Coroutine)
This loop runs inside `AgentEngine`. It must be resilient, secure, and self-correcting.

```kotlin
function runAgentLoop(task):
    state = "STARTING"
    retryCount = 0
    
    while state != "COMPLETED" and state != "FAILED":
        
        // --- PRE-ACTION CHECKS ---
        if Battery < 15% and not Charging:
            pause("Low Battery")
            wait_for_user()
            
        if Network == "MobileData" and Settings.requireWiFi:
            pause("Waiting for Wi-Fi")
            wait_for_network()

        // 1. PERCEPTION (The "Eyes")
        rawUiTree = AccessibilityService.getWindows()
        screenshot = MediaProjection.capture()
        
        // --- SECURITY: DATA LEAKAGE PREVENTION ---
        // CRITICAL: Sanitize before sending to LLM
        sanitizedUiTree = SecurityGuard.sanitize(rawUiTree) 
        if SecurityGuard.containsSensitiveData(rawUiTree):
            // e.g., Password field, Credit Card, "2FA Code"
            mask_screenshot(screenshot)
            log("Sensitive data detected. Masking inputs.")

        // 2. REASONING (The "Brain")
        context = Memory.getRecentHistory()
        prompt = construct_prompt(task, sanitizedUiTree, context)
        
        try:
            plan = LLM.generate_plan(prompt)
        catch NetworkError:
            if retryCount < 3:
                retryCount++
                backoff_sleep()
                continue
            else:
                state = "FAILED"
                notify_user("Network Connection Lost")
                break

        // 3. SAFETY & VALIDATION
        action = plan.nextAction
        
        // check for hallucination
        targetNode = findNode(sanitizedUiTree, action.targetSelector)
        if targetNode is NULL:
            // LLM hallucinated a button that doesn't exist
            log("Element not found: ${action.targetSelector}")
            Memory.addError("Last action failed: Element not found. Look again.")
            continue // Retry loop with new observation

        // check for destructive actions
        if SecurityGuard.isDestructive(action):
            // e.g., Delete email, Transfer money, Factory Reset
            userConfirmed = Overlay.requestConfirmation("Agent wants to: ${action.description}")
            if not userConfirmed:
                state = "FAILED"
                break

        // 4. EXECUTION (The "Hands")
        success = AccessibilityService.perform(action)
        
        // 5. VERIFICATION
        delay(2000) // Wait for UI animation
        newUiTree = AccessibilityService.getWindows()
        
        if isSameState(rawUiTree, newUiTree):
            // Screen didn't change. Action might have failed silently.
            retryCount++
            if retryCount > 3:
                 // STUCK DETECTION
                 Overlay.askUserHelp("I'm stuck. Can you click the button for me?")
                 pause()
        else:
            retryCount = 0 // Reset on progress
            Memory.logAction(action, success)

```

## 2. Handling Edge Cases

### A. The "Infinite Loop" (Stuck in a Menu)
*   **Scenario:** Agent keeps clicking "Back" but the app doesn't exit.
*   **Logic:** Maintain a hash of the last 3 `UiTrees`. If `CurrentHash == Hash[t-1] == Hash[t-2]`, trigger `StuckException`.
*   **Resolution:** Suggest `GlobalAction.HOME` or ask user intervention.

### B. App Crash / ANR (Application Not Responding)
*   **Scenario:** Target app closes unexpectedly or shows "App isn't responding".
*   **Logic:**
    *   Detect system dialog text: "Close app", "Wait".
    *   Action: Click "Wait" once, then "Close app" if recurs.
    *   Recovery: Relaunch the app using `PackageManager`.

### C. Pop-ups & Ads
*   **Scenario:** A wild ad appears blocking the flow.
*   **Logic:**
    *   Maintain a "Global Negative List" of UI text: `["Skip", "Close", "x", "No thanks", "Dismiss"]`.
    *   If the LLM's planned action is blocked, scan specifically for these keywords and click them first.

## 3. Security Guard Module (Pseudocode)

```kotlin
object SecurityGuard {
    
    val SENSITIVE_KEYWORDS = ["password", "cvv", "credit card", "ssn", "social security"]
    
    fun sanitize(uiNode: AccessibilityNodeInfo): String {
        // Recursively walk the tree
        // If node.inputType == TYPE_TEXT_VARIATION_PASSWORD -> Replace text with "*****"
        // If node.text matches EmailRegex -> Replace with "[EMAIL_REDACTED]"
        // Return sanitized JSON string
    }

    fun isDestructive(action: Action): Boolean {
        // 1. Keyword Check
        if (action.text.contains("Delete", "Remove", "Send Money", "Pay")) return true
        
        // 2. Package Check
        if (currentApp == "com.google.android.apps.walletnfcrel") return true // Google Wallet
        if (currentApp == "com.paypal.android.p2pmobile") return true
        
        return false
    }
}
```
