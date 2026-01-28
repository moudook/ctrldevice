<!-- INSTRUCTION: NO EMOJIS ALLOWED IN THIS CODEBASE. -->
# Safety & Governance: Guardrails for the Agent

## 1. The Agent Governor (Circuit Breakers)
An automated system to prevent "Runaway Agents" from draining resources or entering infinite loops.

**Triggers for Emergency Stop:**
1.  **Consecutive Failures:** > 5 failures in the same task node.
2.  **Battery Drain:** Battery drops below 15%.
3.  **Data Usage:** Agent consumes > 100MB in 1 hour.
4.  **Loop Detection:** Agent visits the exact same screen state 3 times in a row.

## 2. The Security Guard (Input Sanitization)
Protects against **Prompt Injection** from malicious apps or websites.

**Sanitization Logic:**
*   **Text Filter:** Before reading screen text to the LLM, remove dangerous keywords (`SYSTEM:`, `IGNORE PREVIOUS`, `rm -rf`).
*   **Visual Redaction:** Blackout password fields and credit card numbers before Vision processing.

## 3. Permission Escalation Defense
The agent is **FORBIDDEN** from performing critical system changes without explicit user confirmation.

**Restricted Actions (Require User Approval):**
*   `GRANT_PERMISSION` (e.g., Camera, Mic)
*   `UNINSTALL_APP`
*   `TRANSFER_MONEY` (Banking apps)
*   `FACTORY_RESET`

## 4. Visual Verification
The agent cannot trust its own internal state. After any critical action (e.g., "Send Email"), it must:
1.  Wait 2 seconds.
2.  Take a screenshot.
3.  Verify: "Do I see the 'Sent' toast or the email in the Sent folder?"