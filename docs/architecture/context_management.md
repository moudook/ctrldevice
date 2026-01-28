# Context Management: Hierarchical Memory & Compression

## 1. The Challenge
Long-horizon tasks (hours) generate too much data for any LLM context window.

## 2. Hierarchical Memory System

| Tier | Name | Storage | Function |
| :--- | :--- | :--- | :--- |
| **L0** | **Working Memory** | RAM | The immediate task. Last 5 steps. Full detail. |
| **L1** | **Session Memory** | Vector DB | Compressed summaries of completed sub-tasks. "Researched Kotlin. Found 3 links." |
| **L2** | **Procedural Memory** | SQLite | Learned UI patterns. "Settings -> Display -> Dark Mode". |
| **L3** | **Long-Term Memory** | Disk | User Preferences. "User likes 1080p video." |

## 3. Context Compression Protocol
When L0 fills up:
1.  **Summarize:** Convert the last 50 actions into a natural language paragraph.
2.  **Embed:** Store the summary in the Vector DB.
3.  **Flush:** Clear L0, retaining only the current Goal and the new Summary.

## 4. Swarm Handoff (Compressed Handoff)
When passing data between agents (e.g., Researcher -> Social):
*   **DO NOT** pass the full research logs.
*   **DO** pass a structured `AgentHandoff` object:
    *   `Summary`: "Found 3 articles."
    *   `KeyPoints`: ["Point A", "Point B"]
    *   `Artifacts`: [Link to PDF]
