package com.ctrldevice.features.agent_engine.memory

import com.ctrldevice.domain.models.AgentAction
import com.ctrldevice.domain.models.TaskSummary

/**
 * Manages the agent's brain capacity to prevent "Context Rot".
 */
class MemoryManager {

    // L0: Short-term (RAM)
    private val workingMemory = mutableListOf<AgentAction>()
    private val MAX_WORKING_SIZE = 50

    // L1: Mid-term (Session)
    private val sessionMemory = mutableListOf<TaskSummary>()

    /**
     * Adds an action to memory. Triggers compression if full.
     */
    fun recordAction(action: AgentAction) {
        workingMemory.add(action)
        if (workingMemory.size >= MAX_WORKING_SIZE) {
            compress()
        }
    }

    private fun compress() {
        val summary = summarize(workingMemory)
        sessionMemory.add(summary)
        workingMemory.clear() // Keep critical state (e.g., current goal)
    }

    private fun summarize(actions: List<AgentAction>): TaskSummary {
        // TODO: Call LLM to summarize these actions into one paragraph
        return TaskSummary("Summarized ${actions.size} actions.")
    }

    /**
     * Retrieves relevant past knowledge using Vector Search.
     */
    fun recall(query: String): List<String> {
        // TODO: Search Procedural Memory (SQLite/Vector)
        return emptyList()
    }
}
