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
 * Specialized agent for Web Browsing and Research tasks.
 * Logic Compressed: Uses table-driven recovery and generic verification polling.
 */
class ResearchAgent(
    id: AgentId,
    resourceManager: ResourceManager,
    messageBus: MessageBus,
    stateManager: StateManager,
    brain: AgentBrain = RuleBasedBrain()
) : ExploratoryAgent(id, resourceManager, messageBus, stateManager, brain) {

    // Logic Compression: Table-Driven Recovery
    override val recoveryStrategies = listOf(
        RecoveryStrategy.Wait,       // Wait longer for web loading
        RecoveryStrategy.ScrollDown, // Scroll to find content
        RecoveryStrategy.Back        // Handle bad redirects
    )

    override suspend fun verifyOutcome(task: TaskNode.AtomicTask): Pair<Boolean, String?> {
        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "thought", data = "Verifying if browser opened..."
        ))

        // Optimization: Smart Polling using generic base method (5s timeout for web)
        val (success, content) = pollVerification(5000L) { content ->
            checkBrowserState(content)
        }

        if (success) {
            emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Verification Success: Browser loaded."
            ))
        } else {
             emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Verification Warning: Browser keywords not found."
            ))
        }

        return Pair(success, content)
    }

    private fun checkBrowserState(content: String): Boolean {
        // Check for common browser UI elements or the query text
        return content.contains("Chrome", ignoreCase = true) ||
               content.contains("Search", ignoreCase = true) ||
               content.contains("Google", ignoreCase = true) ||
               content.contains("Address", ignoreCase = true)
    }
}
