# Android AI Agent Architecture

## Overview
This architecture is designed for a native Android application where the "Agent Brain" runs as a Kotlin coroutine-based state machine. It communicates with an external LLM (Local via USB/WiFi or API) but executes logic directly on the device.

## Core Components

### 1. Agent Engine (`com.ctrldevice.agent.engine`)
*   **Role:** The central nervous system.
*   **Pattern:** State Machine (States: `IDLE`, `PLANNING`, `EXECUTING`, `WAITING`, `PAUSED`).
*   **Implementation:** Kotlin Coroutines & Flow.

### 2. Accessibility Controller (`com.ctrldevice.service.accessibility`)
*   **Role:** The "Hands" and "Eyes".
*   **Function:**
    *   Reads screen content (View hierarchy).
    *   Performs gestures (Click, Scroll, Text Entry).
    *   **CRITICAL:** Intercepts `MotionEvent` to detect user touch and trigger `PAUSE`.

### 3. Overlay Service (`com.ctrldevice.service.overlay`)
*   **Role:** The "Face".
*   **Function:** Draws a floating bubble/window showing the agent's current thought/status.
*   **Transparency:** Always visible so the user knows what's happening.

### 4. Memory (`com.ctrldevice.data.room`)
*   **Role:** Long-term storage.
*   **Tech:** Room Database (SQLite).
*   **Entities:**
    *   `Task`: The high-level goal (e.g., "Find internship").
    *   `Step`: Individual actions taken.
    *   `Knowledge`: Vector embeddings or structured data extracted from apps.

## Data Flow
1.  **User** speaks command -> **STT (Speech-to-Text)**.
2.  **Agent Engine** receives text -> Sends to **LLM Client**.
3.  **LLM** returns plan (JSON).
4.  **Agent Engine** executes Step 1 via **Accessibility Service**.
5.  **Agent Engine** waits for screen update.
6.  **Accessibility Service** returns new screen info.
7.  **Loop** continues.

## Safety & Control
*   **Touch Interrupt:** If the user touches the screen, the Accessibility Service detects `ACTION_DOWN` and immediately sends a `PAUSE` signal to the Agent Engine.
*   **Visual Feedback:** The Overlay must always show the current action *before* it happens (e.g., "Clicking 'Send' in 3s...").
