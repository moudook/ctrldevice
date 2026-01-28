# CtrlDevice: The Symbiotic Android Intelligence (Full Project Definition)

## 1. The Core Philosophy: "Ghost in the Shell"
**CtrlDevice** represents a paradigm shift in AI-Device interaction. It rejects the traditional model of an "Assistant" that acts as an intermediary or a tool. Instead, it implements a **Symbiotic Architecture** where the AI Agent and the Android System function as a single, unified entity.

*   **The Body:** The Android Device (Hardware + OS).
*   **The Nervous System:** The Operating System (Broadcasts, Accessibility Services, Sensors).
*   **The Mind:** The AI Agent (Orchestrator + Swarm).

In this model, the agent does not "use" the phone; it **inhabits** it. It possesses **Proprioception** (self-awareness of battery, network, screen state) and operates with **Exploratory Agency** (learning by doing), mirroring how a human interacts with their own physical body and environment.

## 2. Capability Scope: "The Digital Employee"
The agent is designed for **General Purpose Autonomy** with a focus on **Long-Horizon, Multi-Step Workflows**. It is capable of replacing a human operator for tasks that span hours and require juggling multiple applications.

### 2.1 The "Employee" Standard
Just as you would ask a human employee to "Research this topic and produce a report," you can ask CtrlDevice to:
*   **Research:** Browse the web (Chrome), manage multiple tabs, bypass simple obstacles (like Captchas via visual reasoning), and synthesize information.
*   **Communicate:** Negotiate scheduling via WhatsApp/Teams, matching tone and context.
*   **Create:** Generate notes, documents, or media playlists based on findings.
*   **Curate:** Filter content (e.g., "Find 50 relevant videos") using visual and semantic analysis.

### 2.2 Universal Tool Use
The agent follows the rule: **"If a human can do it, I can do it."**
*   **No API Dependency:** It does not rely on fragile, hidden APIs for every app.
*   **Visual Grounding:** It "sees" the screen (Accessibility Nodes + Pixel Vision).
*   **Dynamic Discovery:** If it needs to book a hotel, it checks for installed apps (Airbnb). If missing, it seamlessly falls back to the Browser (Chrome), just like a human would.

## 3. The Cognitive Architecture: Hierarchical Swarm
To handle the complexity of "Long Horizon" tasks without "Context Rot" (memory overload), the system uses a **Hierarchical Multi-Agent System (HMAS)**.

### 3.1 The Orchestrator Agent (The Ego)
*   **Role:** The persistent "Self". It maintains the high-level goal and user preferences.
*   **Function:**
    *   **Planning:** Breaks a vague request ("Launch product") into a Directed Acyclic Graph (DAG) of sub-tasks.
    *   **Resource Management:** Allocates "Brain Power" (RAM/Context) to sub-agents.
    *   **Swarm Management:** Spawns, monitors, and kills sub-agents.

### 3.2 The Swarm (The Skills)
Ephemeral, specialized agents that spin up to perform a specific job and then dissolve, passing only their *results* back to the Orchestrator.
*   **Research Swarm:** Web browsing, tab management, data extraction.
*   **Social Swarm:** Chatting, email, calendar negotiation.
*   **Media Swarm:** YouTube search, playlist management, playback control.
*   **System Swarm:** File management, settings configuration, app installation.

### 3.3 Mixture of Experts (MoE) Routing
The Orchestrator uses a **Semantic Router** to dynamically assign tasks.
*   *Task:* "Find a funny joke." -> *Router:* "Assign to Social Agent (Specialty: Tone/Humor)."
*   *Task:* "Download this file." -> *Router:* "Assign to System Agent (Specialty: Files)."

## 4. The Operational Mode: Exploratory Agency
The agent rejects rigid, hard-coded scripts in favor of **Resilient Exploration**.

### 4.1 The "Act-Think-Act" Loop
1.  **Observe:** Capture the screen state (Vision + Hierarchy).
2.  **Reflect:** Compare current state to goal. "Am I closer?"
3.  **Act:** Execute a gesture (Click, Scroll, Type).
4.  **Loop:** If the action failed (e.g., button didn't work), try a different path.
    *   *Example:* "I can't find 'Dark Mode' in Display Settings. I will try searching for it in the Settings search bar instead."

### 4.2 Control > Optimization
We do not optimize for execution speed (milliseconds). We optimize for **Success Rate**. The agent is allowed to "play" with the device—clicking menus, exploring options—until it finds the solution, even if it takes time.

## 5. Technical Implementation (Native Android)

### 5.1 The Stack
*   **Language:** Kotlin (Native Performance).
*   **Architecture:** Clean Architecture + MVVM + Hilt (DI).
*   **Brain:** Local LLM (USB/WiFi) or Cloud API (HuggingFace), abstracted behind a `BrainInterface`.

### 5.2 The "Senses" (Proprioception)
The `ProprioceptionSystem` uses Kotlin Flows to stream device state directly into the agent's logic:
*   **Vision:** `AccessibilityService` (View Hierarchy) + `MediaProjection` (Screenshots).
*   **Touch:** `AccessibilityService` (Gesture Injection).
*   **Energy:** `BatteryManager` (Fatigue).
*   **Connectivity:** `ConnectivityManager` (Speech ability).

### 5.3 Safety & Security
*   **User Interrupt:** "The User is God." Any physical touch by the user creates an interrupt signal (`ACTION_DOWN`) that immediately PAUSES the agent.
*   **Data Sanitization:** A `SecurityGuard` layer acts as a firewall, redacting PII (passwords, emails) from screen data *before* it reaches the LLM.
*   **Visual Verification:** A `VerificationAgent` runs after critical actions to visually confirm success (e.g., "Did the checkmark appear?") before moving on.

## 6. Summary
CtrlDevice is not just an app; it is an **autonomous digital entity**. It turns the Android device into a self-driving vehicle for information work. It is robust, adaptive, and capable of executing the kinds of messy, complex, cross-application workflows that previously required a human human.
