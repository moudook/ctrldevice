# Resilient Exploration: The "Ralph Loop" for Android

## 1. The Core Concept
The CtrlDevice agent implements the **Ralph Loop**: an autonomous iterative loop where the agent keeps retrying a task until completion, with each iteration learning from the previous failure.

**Traditional AI:** Tries once -> Fails -> Stops.
**CtrlDevice (Ralph):** Tries -> Fails -> Observes -> Thinks -> Retries -> Succeeds.

## 2. The Loop Structure

1.  **Plan:** "I need to click 'Dark Mode'."
2.  **Act:** Attempt Action A (Click "Display").
3.  **Observe:** Did it work? (No, I don't see 'Dark Mode').
4.  **Reflect (The Learning Step):** "Action A failed. The button isn't here."
5.  **Pivot:** "I will try Action B (Search 'Theme' in Settings)."
6.  **Retry:** Execute Action B.

## 3. Key Components

### A. State Persistence
We store the history of attempts in a local database (`StepAttemptEntity`).
*   *Attempt 1:* Click(500, 200) -> Result: No Change.
*   *Attempt 2:* ScrollDown() -> Result: New text visible.

### B. Learning Memory (Procedural Memory)
When a task eventually succeeds, we store the successful path.
*   *Learned Pattern:* "To enable Dark Mode on this device, search for 'Theme', not 'Display'."
*   *Next Time:* Skip the failure steps and go straight to the solution.

### C. Max Iterations (The Limit)
To prevent infinite loops, every sub-task has a `max_retries` counter (e.g., 5). If reached, the agent triggers a **Circuit Breaker**.
