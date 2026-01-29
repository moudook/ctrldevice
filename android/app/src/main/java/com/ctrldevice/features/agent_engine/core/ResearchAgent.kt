package com.ctrldevice.features.agent_engine.core

import android.util.Log
import kotlinx.coroutines.delay
import com.ctrldevice.agent.tools.ToolRegistry
import com.ctrldevice.features.agent_engine.coordination.*
import com.ctrldevice.features.agent_engine.intelligence.RuleBasedBrain
import kotlinx.datetime.Clock

/**
 * Specialized agent for Web Browsing and Research tasks.
 */
class ResearchAgent(
    id: AgentId,
    resourceManager: ResourceManager,
    messageBus: MessageBus,
    stateManager: StateManager
) : ExploratoryAgent(id, resourceManager, messageBus, stateManager) {

    private val brain = RuleBasedBrain()

    override suspend fun performAction(strategy: String, task: TaskNode.AtomicTask): Boolean {
        Log.d("ResearchAgent", "Performing action for task: ${task.description} with strategy: $strategy")

        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "thought", data = "I need to research: ${task.description}. Strategy: $strategy"
        ))

        // 1. Gather Context
        var screenContext = ""
        val screenReader = ToolRegistry.getTool("read_screen")
        if (screenReader != null) {
            val result = screenReader.execute("{}")
            if (result.success) {
                screenContext = result.output
            }
        }

        val history = stateManager.getActionHistory(id).map { it.description }

        // 2. Consult the Brain
        val thought = brain.proposeNextStep(task, screenContext, history)

        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "thought", data = "Brain Reasoning: ${thought.reasoning}"
        ))

        val tool = ToolRegistry.getTool(thought.toolName)
        if (tool == null) {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "error", data = "Brain proposed unknown tool: ${thought.toolName}"
            ))
            return false
        }

        val params = thought.toolParams

        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "action", data = "Executing ${tool.name} with params: $params"
        ))

        // Log to Memory
        stateManager.logAction(id, "Executed ${tool.name} with params: $params (Strategy: $strategy)")

        val result = tool.execute(params)
        Log.d("ResearchAgent", "Tool result: $result")

        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "observation", data = "Tool Output: ${result.output}"
        ))

        return result.success
    }

    override suspend fun verifyOutcome(task: TaskNode.AtomicTask): Boolean {
        // Wait for browser to open/load
        delay(2000)

        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "thought", data = "Verifying if browser opened..."
        ))

        val screenReaderTool = ToolRegistry.getTool("read_screen")
        if (screenReaderTool == null) {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Verification Warning: ScreenReaderTool not found."
            ))
            return false
        }

        val screenResult = screenReaderTool.execute("{}")
        if (!screenResult.success) {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Verification Warning: Could not read screen."
            ))
            return false
        }

        val content = screenResult.output
        // Check for common browser UI elements or the query text
        val valid = content.contains("Chrome", ignoreCase = true) ||
                   content.contains("Search", ignoreCase = true) ||
                   content.contains("Google", ignoreCase = true) ||
                   content.contains("Address", ignoreCase = true)

        if (!valid) {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Verification Warning: Didn't see browser keywords in screen content."
            ))
        } else {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Verification Success: Browser appears to be open."
            ))
        }
        return valid
    }

    override fun pivotStrategy(current: String, attempt: Int): String {
        return "RETRY_SEARCH_Strategy_$attempt"
    }
}
