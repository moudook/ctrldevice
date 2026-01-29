package com.ctrldevice.features.agent_engine.core

import android.util.Log
import kotlinx.coroutines.delay
import com.ctrldevice.agent.tools.ToolRegistry
import com.ctrldevice.features.agent_engine.coordination.*
import com.ctrldevice.features.agent_engine.intelligence.RuleBasedBrain
import kotlinx.datetime.Clock

/**
 * specialized agent for System tasks (Global actions, Files, Settings).
 */
class SystemAgent(
    id: AgentId,
    resourceManager: ResourceManager,
    messageBus: MessageBus,
    stateManager: StateManager
) : ExploratoryAgent(id, resourceManager, messageBus, stateManager) {

    private val brain = RuleBasedBrain()

    override suspend fun performAction(strategy: String, task: TaskNode.AtomicTask): Boolean {
        Log.d("SystemAgent", "Performing action for task: ${task.description} with strategy: $strategy")

        // Handle Recovery Strategies
        if (strategy == "SCROLL_DOWN_AND_RETRY") {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "action", data = "Recovery: Scrolling down to find element..."
            ))
            val scrollTool = ToolRegistry.getTool("scroll")
            if (scrollTool != null) {
                scrollTool.execute("down")
                delay(1000) // Wait for scroll animation
            }
        } else if (strategy == "WAIT_AND_RETRY") {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "action", data = "Recovery: Waiting for UI to load..."
            ))
            delay(3000)
        } else if (strategy == "BACK_AND_RETRY") {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "action", data = "Recovery: Going back to previous screen..."
            ))
            val backTool = ToolRegistry.getTool("go_back")
            if (backTool != null) {
                backTool.execute("{}")
                delay(1500) // Wait for transition
            }
        }

        // 1. Gather Context (with Retry)
        var screenContext = ""
        val screenReader = ToolRegistry.getTool("read_screen")
        if (screenReader != null) {
            var readAttempts = 0
            while (readAttempts < 3) {
                val result = screenReader.execute("{}")
                if (result.success && result.output.isNotBlank()) {
                    screenContext = result.output
                    break
                }
                readAttempts++
                if (readAttempts < 3) {
                    emit(Message.DataAvailable(
                        from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                        key = "log", data = "Screen read failed or empty (Attempt $readAttempts). Retrying..."
                    ))
                    delay(500)
                }
            }
        }

        val history = stateManager.getActionHistory(id).map { it.description }

        // 2. Consult the Brain
        val thought = brain.proposeNextStep(task, screenContext, history)

        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "thought", data = "Brain Reasoning: ${thought.reasoning}"
        ))

        // Execute Tool proposed by Brain
        val tool = ToolRegistry.getTool(thought.toolName)

        if (tool != null) {
            val params = thought.toolParams

            emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "action", data = "Executing ${tool.name} with params: '$params'..."
            ))

            // Log to Memory
            stateManager.logAction(id, "Executed ${tool.name} with params: $params (Strategy: $strategy)")

            val result = tool.execute(params)
            Log.d("SystemAgent", "Tool result: $result")

            emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "observation", data = "Tool Output: ${result.output}"
            ))

            return result.success
        } else {
            emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Brain proposed unknown tool: ${thought.toolName}"
            ))
            return false
        }
    }

    override suspend fun verifyOutcome(task: TaskNode.AtomicTask): Boolean {
        // Give the UI time to settle (animations, load times)
        delay(1500)

        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "thought", data = "Verifying outcome for: ${task.description}"
        ))

        // 1. Snapshot Screen using Registry lookup
        val screenReader = ToolRegistry.getTool("read_screen")
        if (screenReader == null) {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Verification Error: ScreenReaderTool not found."
            ))
            return false
        }

        val screenResult = screenReader.execute("{}")
        if (!screenResult.success) {
            emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Verification Warning: Could not read screen."
            ))
            return false
        }
        val content = screenResult.output

        // 2. Check against expectations
        if (task.description.contains("settings", ignoreCase = true)) {
            val valid = content.contains("Settings", ignoreCase = true) ||
                       content.contains("Network", ignoreCase = true) ||
                       content.contains("Battery", ignoreCase = true)

            if (!valid) {
                 emit(Message.DataAvailable(
                    from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                    key = "thought", data = "Verification Failed: Expected Settings screen, but didn't see keywords."
                ))
            }
            return valid
        } else if (task.description.contains("type", ignoreCase = true)) {
             val expectedText = task.description.replace("type", "", ignoreCase = true).trim()
             val valid = content.contains(expectedText, ignoreCase = true)
             if (!valid) {
                 emit(Message.DataAvailable(
                    from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                    key = "thought", data = "Verification Failed: Typed text '$expectedText' not found on screen."
                ))
            }
            return valid
        }

        // For navigation tasks, we might need to check if we left the previous screen.
        // For now, default to true for others.
        return true
    }

    override fun pivotStrategy(current: String, attempt: Int): String {
        // If the first attempt failed (attempt 0 just finished), try scrolling.
        if (attempt == 0) {
            return "SCROLL_DOWN_AND_RETRY"
        }
        // If scrolling didn't help (attempt 1 just finished), try waiting.
        if (attempt == 1) {
            return "WAIT_AND_RETRY"
        }
        // If waiting didn't help (attempt 2 just finished), try going back.
        if (attempt == 2) {
            return "BACK_AND_RETRY"
        }
        return "RETRY_Strategy_$attempt"
    }
}
