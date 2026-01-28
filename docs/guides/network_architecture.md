# Network Architecture & Connectivity

## Overview
The Agent Logic runs locally on the device, but the "Brain" (LLM) is accessed via the Internet. This separates the *control* (which must be local and fast) from the *intelligence* (which is heavy and requires cloud GPUs).

## Data Flow
1.  **User Input:** Voice/Text -> Captured by `AgentEngine`.
2.  **Context Building:** `AgentEngine` gathers screen data + history.
3.  **Sanitization:** `SecurityGuard` removes PII.
4.  **Network Request:** `LlmApiService` sends JSON to Cloud API.
5.  **Response:** JSON Plan returned.
6.  **Action:** `AgentEngine` executes locally.

## Permissions Required
In `AndroidManifest.xml`:
```xml
<!-- Required to talk to the Cloud LLM -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Required to check if we are on WiFi (to save data) -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Security Configuration
We use a strict `network_security_config.xml`.
*   **Production:** HTTPS only. No cleartext traffic allowed.
*   **Development:** HTTP allowed only for `localhost` (for testing with local servers).

## Handling Network Latency
Since the "Brain" is remote, there will be latency (1-3 seconds).
*   **Visual Feedback:** The Overlay must show a "Thinking..." animation.
*   **Timeouts:** Set to 60s. Complex queries take time.
*   **Retries:** Use `ExponentialBackoff` for transient network failures.

## Privacy Note
The Agent is the **only** component allowed to export sanitized screen data. No other analytics or tracking SDKs should be installed in this app.
