# Long Horizon Task Strategy

## The Challenge
Tasks like "Monitor email for an internship reply" or "Research a topic for 3 hours" exceed the standard request-response cycle. They require:
1.  **Persistence:** The agent must survive app restarts or device reboots.
2.  **Context Management:** We cannot feed 3 hours of logs into the LLM. We must summarize and store history.
3.  **Asynchronous Waiting:** The agent must be able to "sleep" and "wake up" without blocking.

## Architecture

### 1. The "State Machine" Approach
Every long-running task is a State Machine.
*   **State:** `WAITING_FOR_EMAIL`
*   **Context:** `{ "last_check": "10:00 AM", "target_sender": "hr@google.com" }`
*   **Action:** Check Gmail -> If found, transition to `PROCESSING`; If not, `SLEEP` for 10 mins.

### 2. The Memory Hierarchy
*   **Working Memory (RAM):** The current step's immediate observations. Passed to LLM.
*   **Episodic Memory (Vector DB):** Important past events (e.g., "Tried to forward email but button was disabled"). Retrieved when relevant.
*   **Checkpoint Store (Disk/SQL):** The "Save Game" file. Stores the exact State and Context variables.

### 3. Execution Loop
1.  **Load Checkpoint:** Where did I leave off?
2.  **Perceive:** Look at the screen.
3.  **Decide:** LLM decides next action based on State + Perception.
4.  **Act:** Perform action.
5.  **Update State:** Change status if needed.
6.  **Checkpoint:** Save to disk IMMEDIATELY.
