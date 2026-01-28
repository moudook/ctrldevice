# Multi-Agent Coordination System (The Operating System)

## 1. The Core Architecture
CtrlDevice runs an **Operating System for Agents** on top of Android. It solves the critical problems of **Resource Conflicts** (two agents wanting Chrome) and **Priority Inversion** (important tasks waiting for trivial ones).

## 2. Component Breakdown

### A. Resource Manager (The Bouncer)
*   **Role:** Arbitrates access to the device's finite resources.
*   **Resources Managed:**
    *   `Resource.App`: Specific package names (e.g., `com.android.chrome`).
    *   `Resource.Screen`: Exclusive UI access.
    *   `Resource.Network`: Bandwidth priority.
*   **Mechanism:** Hierarchical Locking with **Priority Preemption**.
    *   If a High-Priority "Social Agent" needs Chrome, the Low-Priority "Research Agent" is **Preempted** (paused and checkpointed) immediately.

### B. Task Graph (The Choreographer)
*   **Role:** Manages the dependency tree of the mission.
*   **Structure:** Directed Acyclic Graph (DAG).
    *   **AtomicTask:** Single unit of work.
    *   **ParallelGroup:** Tasks that run simultaneously (if resources allow).
    *   **SequentialGroup:** Tasks that must run in order.
*   **Data Flow:** Handles the piping of output data from Task A to Task B (e.g., Research Summary -> Email Drafter).

### C. Message Bus (The Nervous System)
*   **Role:** Event-driven communication backbone. Agents **never** call each other directly.
*   **Events:**
    *   `ResourcePreempted`: "Stop working, someone important needs the screen."
    *   `DataAvailable`: "I found the file you needed."
    *   `ErrorOccurred`: "I failed. Help me."

### D. State Manager (The Save System)
*   **Role:** Persists agent state to disk/DB.
*   **Function:** When an agent is preempted, its entire state (Progress, Variables, Resources Held) is serialized. This allows it to be **Hydrated** back to life exactly where it left off once the resource is free.

## 3. Execution Flow (The "CtrlDeviceCore" Loop)
1.  **Orchestrator:** Receives User Request -> Generates `TaskGraph`.
2.  **GraphExecutor:**
    *   Identifies `ReadyTasks` (dependencies met).
    *   Spawns Agents.
    *   Requests `ResourceLeases` from ResourceManager.
    *   If acquired -> Runs Task.
    *   If blocked -> Waits in Priority Queue.
3.  **Completion:** Agent finishes -> Updates Graph -> Triggers dependent tasks.

## 4. Debugging & Visualization
The `CoordinationDebugger` provides a live view of the system:
*   **Mermaid Diagrams** for the Task Graph.
*   **Lease Table** showing who owns what app.
*   **Message Log** showing the chatter between agents.
