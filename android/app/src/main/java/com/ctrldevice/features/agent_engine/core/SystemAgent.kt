package com.ctrldevice.features.agent_engine.core

import android.util.Log
import kotlinx.coroutines.delay
import com.ctrldevice.agent.tools.ToolRegistry
import com.ctrldevice.features.agent_engine.coordination.*
import com.ctrldevice.features.agent_engine.strategies.RecoveryStrategy
import com.ctrldevice.features.agent_engine.intelligence.AgentBrain
import com.ctrldevice.features.agent_engine.intelligence.RuleBasedBrain
import kotlinx.datetime.Clock

/**
 * Specialized agent for System tasks (Global actions, Files, Settings).
 * Logic Compressed: Uses table-driven recovery and generic verification polling.
 */
class SystemAgent(
    id: AgentId,
    resourceManager: ResourceManager,
    messageBus: MessageBus,
    stateManager: StateManager,
    brain: AgentBrain = RuleBasedBrain()
) : ExploratoryAgent(id, resourceManager, messageBus, stateManager, brain) {

    // Logic Compression: Table-Driven Recovery Strategies
    override val recoveryStrategies = listOf(
        RecoveryStrategy.ScrollDown,
        RecoveryStrategy.Wait,
        RecoveryStrategy.Back
    )

    override suspend fun verifyOutcome(task: TaskNode.AtomicTask): Pair<Boolean, String?> {
        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "thought", data = "Verifying outcome for: ${task.description}"
        ))

        // Optimization: Use generic poller from base class (Smart Polling)
        val (success, content) = pollVerification(2000L) { content ->
            checkExpectations(task, content)
        }

        if (!success) {
            emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Verification Failed: Expected state not found."
            ))
        }

        return Pair(success, content)
    }

    private fun checkExpectations(task: TaskNode.AtomicTask, content: String): Boolean {
        if (task.description.contains("settings", ignoreCase = true)) {
            return content.contains("Settings", ignoreCase = true) ||
                   content.contains("Network", ignoreCase = true) ||
                   content.contains("Battery", ignoreCase = true)
        } else if (task.description.contains("type", ignoreCase = true)) {
             val expectedText = task.description.replace("type", "", ignoreCase = true).trim()
             return content.contains(expectedText, ignoreCase = true)
        }
        // Default for navigation or unknown tasks
        return true
    }
}
