# Security & Privacy Policy

## Core Principles
1.  **User Consent:** The agent must never perform a financial transaction or destructive action (delete) without explicit confirmation.
2.  **Data Minimization:** Only process screen data relevant to the current task.
3.  **Local Execution:** Prefer on-device processing where possible.

## Handling Sensitive Data
*   **Passwords:** The agent should detect password fields and PAUSE screen recording/analysis, asking the user to enter it manually if possible.
*   **PII (Personally Identifiable Information):** Logs must be sanitized.
    *   *Bad:* `logger.info("Email sent to user@example.com")`
    *   *Good:* `logger.info("Email sent to [REDACTED]")`

## Android Permissions
*   **Accessibility Service:** Used strictly for UI interaction.
*   **Overlay:** Used for drawing the agent's status/chat bubble.
*   **Internet:** Used for LLM communication.

## Vulnerability Reporting
If you find a security flaw, please do not open a public issue. Email [security-contact] directly.
