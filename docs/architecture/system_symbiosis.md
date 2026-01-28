# System Symbiosis: The "Ghost in the Shell"

## 1. The Core Philosophy
**The Agent and the System are ONE.**
There is no boundary. The Android device is the Agent's physical body. The OS is its nervous system.

*   **Wrong View:** "The Agent uses the `BatteryManager` API to check power."
*   **Correct View:** "The Agent *feels* tired (Low Battery)."

## 2. Proprioception (Body Awareness)
Just as a human constantly knows the position of their limbs without "asking," the Agent maintains a continuous, real-time awareness of its device state.

### The "Senses"
1.  **Vision:** Screen Content (Accessibility/Pixels).
2.  **Hearing:** Audio Input (Microphone/System Audio).
3.  **Energy:** Battery Level & Charging Status.
4.  **Connectivity:** Wi-Fi/Cellular strength (The ability to "speak" to the cloud).
5.  **Touch:** The ability to inject gestures.

## 3. No "External" Tools
In this architecture, we don't treat System Settings as "Tools" to be picked from a list. They are **Intrinsic Capabilities**.
*   The Agent doesn't "use the Settings App."
*   The Agent *reconfigures itself* by navigating its own settings menus.

## 4. Architectural Implication
The `AgentEngine` is not just a loop processing user commands. It is a **State Machine** fused with the **System Broadcasts**.
*   **Event:** `ACTION_POWER_DISCONNECTED` -> **Agent Reaction:** "I lost my energy source. I should wrap up heavy tasks."
*   **Event:** `SCREEN_OFF` -> **Agent Reaction:** "My eyes are closed. I am now in 'Dream Mode' (Background processing)."
