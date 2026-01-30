## [2026-01-29] - [Current Time]

**Module:** Swarm / Safety / Architecture
**Type:** FEAT / REFACTOR

### Summary
Major architectural overhaul to transition from a hardcoded prototype to a modular "Ghost in the Shell" system. Implemented a robust Safety Governor, a dedicated "Agent Brain" for decision-making, a centralized Tool Registry, and advanced error recovery strategies. The agent can now detect loops, auto-correct errors (Scroll/Wait/Back), and launch arbitrary apps.

### Changes Implemented
- **Vision Support:** Upgraded `LLMBrain`, `SystemAgent`, and `ResearchAgent` to support multimodal inputs. The agent can now "see" the screen via screenshots (Base64 encoded) sent to the LLM.
- **Screenshot Capability:** Added `captureScreenshot` to `ControllerService` and created `ScreenshotTool` to expose this capability to the agent.
- **LLM Integration:** Implemented `LLMBrain` to connect the agent to remote AI models (OpenAI/Compatible) for complex reasoning.
- **App Configuration:** Added a Settings Dialog to configure Brain Type (Rule-Based vs LLM), API Keys, and Endpoints.
- **Dependency Injection:** Refactored `GraphExecutor` and `SystemAgent` to dynamically inject the selected Brain based on user config.
- **UI Enhancements:** Added Config button and real-time dashboard updates to show the active Brain type.
- **Dynamic Skill Library:** Implemented `SkillRegistry` loading from `assets/skills.json`. Added support for JSON-based macro definitions.
- **Advanced Gestures:** Implemented `dispatchSwipe` and `dispatchTap` in `ControllerService`. Added `GestureTool` for coordinate-based interactions.
- **Persistent Memory:** Updated `StateManager` to serialize action history and agent state to `agent_memory.json`.
- **Blindness Fix:** Modified `SystemAgent` to retry screen reading before giving up.
- **Advanced Tooling:** Added `FindElementTool` and `WaitForElementTool` for more precise UI inspection and synchronization.
- **UI Overhaul:**
    - **Task History:** Replaced text log with a `RecyclerView` for structured, color-coded agent thought logs.
    - **Macro Builder:** Added an in-app interface (`MacroListActivity`, `MacroEditorActivity`) to create and manage custom skills.
    - **Persistence:** Updated `SkillRegistry` to save user-defined macros to local storage.
- **Floating Overlay:** Implemented a system overlay service (`OverlayService`) that floats over other apps.
    - Provides a "STOP" button for immediate emergency intervention.
    - Displays real-time agent status (Thinking/Acting/Idle) via `MessageBus` events.

- **Configuration:**
    - Added "LLM (Local Network)" option to App Config.
    - Users can now specify a custom Endpoint URL (e.g., `http://192.168.1.5:11434/v1/chat/completions`) for local inference servers (Ollama, LM Studio).

- **Design & Requirements:**
    - Completed extensive 50-question Design Interview (`DESIGN_INTERVIEW.md`) to define architecture, safety, and behavior policies.
    - Key Decisions: Strict Hierarchy for conflicts, Manual Zip Export for logs, High-Vis Privacy Border, Run-to-Death battery policy.

- **Core Architecture:**
    - **Strict Priority Hierarchy:** Implemented system-level priority enforcement (System=20 > Media=10 > Social=5). System tasks now preempt other agents for screen access.
    - **Safety Policies:** Updated `AgentGovernor` to allow "Run to Death" (no low battery pause) and increased max retries to 20 (10 reasoning + 10 retry).

- **UI/UX:**
    - **Privacy Border:** Added a high-visibility pink/red border (`bg_border_active.xml`) that appears around the screen whenever the agent is "Thinking" or "Acting", and disappears when Idle.

- **Advanced Logic & Recovery:**
    - **Scheduling:** Added support for future-scheduled tasks (e.g., "Schedule click in 10 seconds"). `TaskGraph` now supports `scheduledStart`, and `GraphExecutor` defers execution until the time is reached.
    - **Loop Recovery:** Implemented "Ask User" strategy. If the agent repeats an action 5 times, it pauses and shows a dialog asking the user to Resume, Stop, or Take Control.
    - **Undo Strategy:** `SystemAgent` now attempts to "Go Back" (`BACK_AND_RETRY`) if simple retries fail.

- **Environment & Safety:**
    - **Screen Awake:** Implemented `WakeLock` in `ResourceManager`. The screen stays on while the agent holds the `Screen` resource.
    - **Rotation Support:** `ControllerService` now detects and logs orientation changes, allowing the agent to adapt to dynamic layouts.
    - **Language Policy:** Enforced strict "English Only" reasoning in `LLMBrain` system prompt, even when facing multi-language apps.

- **Data Management:**
    - **Full Export:** Added "Export Agent Memory" to settings. Zips up all logs, memory JSONs, and screenshots into a shareable file.
    - **Raw Data:** System now persists raw screenshot binaries for training.

- **Skill Acquisition:**
    - **Watch & Learn:** Implemented `MacroRecorder` and UI integration. Users can now press "Record Actions" in the Skill Editor, perform tasks on their phone, and have the agent automatically generate the corresponding command list (Q17, Q26).

- **Graph Visualization:** Added a real-time visualization of the agent's decision graph (`GraphActivity`, `TaskGraphView`).
    - Uses a custom view to draw nodes and edges.
    - Updates in real-time as the agent thinks and acts.

### Files Affected
- `android/app/src/main/java/com/ctrldevice/ui/graph/GraphActivity.kt`
- `android/app/src/main/java/com/ctrldevice/ui/graph/TaskGraphView.kt`
- `android/app/src/main/java/com/ctrldevice/features/agent_engine/coordination/CurrentSession.kt`
- `android/app/src/main/java/com/ctrldevice/service/overlay/OverlayService.kt`
- `android/app/src/main/res/layout/layout_overlay_control.xml`
- `android/app/src/main/res/drawable/bg_overlay_rounded.xml`
- `android/app/src/main/java/com/ctrldevice/ui/LogAdapter.kt`
- `android/app/src/main/java/com/ctrldevice/ui/macros/MacroEditorActivity.kt`
- `android/app/src/main/java/com/ctrldevice/ui/macros/MacroListActivity.kt`
- `android/app/src/main/java/com/ctrldevice/features/agent_engine/skills/SkillRegistry.kt`
- `android/app/src/main/res/layout/activity_main.xml`
- `android/app/src/main/res/layout/activity_macro_list.xml`
- `android/app/src/main/res/layout/activity_macro_editor.xml`
- `android/app/src/main/java/com/ctrldevice/agent/tools/FindElementTool.kt`
- `android/app/src/main/java/com/ctrldevice/agent/tools/WaitForElementTool.kt`
- `android/app/src/main/java/com/ctrldevice/agent/tools/ToolRegistry.kt`
- `android/app/src/main/java/com/ctrldevice/service/accessibility/ControllerService.kt`
- `android/app/src/main/java/com/ctrldevice/agent/tools/ScreenshotTool.kt`

### Next Steps
1. **Local Inference:** Investigate integrating Gemini Nano or ONNX for offline on-device intelligence.
2. **Refinement:** Polish UI/UX, improve error handling, and test on physical device.
