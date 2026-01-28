package com.ctrldevice.agent.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Brain of the operation.
 */
interface AgentEngine {
    
    val currentState: StateFlow<AgentState>

    /**
     * Starts a new long-running task.
     * @param instruction Natural language instruction from user.
     */
    suspend fun startTask(instruction: String)

    /**
     * Pauses execution immediately.
     * Called when user touches the screen.
     */
    fun pause()

    /**
     * Resumes execution from the saved state.
     */
    fun resume()

    /**
     * Stops and cancels the current task.
     */
    fun stop()
}

sealed class AgentState {
    object Idle : AgentState()
    data class Planning(val instruction: String) : AgentState()
    data class Executing(val stepDescription: String, val tool: String) : AgentState()
    data class Waiting(val reason: String) : AgentState() // e.g., "Waiting for email"
    object Paused : AgentState()
    data class Error(val message: String) : AgentState()
}
