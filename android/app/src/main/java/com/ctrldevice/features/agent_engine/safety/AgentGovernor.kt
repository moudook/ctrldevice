package com.ctrldevice.features.agent_engine.safety

import com.ctrldevice.features.agent_engine.coordination.TaskResult
import com.ctrldevice.features.agent_engine.coordination.StateManager
import com.ctrldevice.features.agent_engine.coordination.AgentId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.ctrldevice.features.agent_engine.coordination.AgentAction

/**
 * The "Circuit Breaker" to prevent runaway agents.
 * Monitors failures, resource usage, and explicit user stops.
 */
class AgentGovernor(
    private val stateManager: StateManager
) {

    private var consecutiveFailures = 0
    private val MAX_RETRIES = 20 // Updated per Design Interview Q9 (10 + 10 retries)
    // private val HOURLY_DATA_LIMIT_MB = 100 // Future feature
    // private var dataUsedMb = 0.0

    private val _isEmergencyStop = MutableStateFlow(false)
    val isEmergencyStop = _isEmergencyStop.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    fun triggerEmergencyStop() {
        _isEmergencyStop.value = true
    }

    fun pauseForIntervention() {
        _isPaused.value = true
    }

    fun resumeFromIntervention() {
        _isPaused.value = false
        // Reset failure counters on resume to give it a fresh chance
        consecutiveFailures = 0
    }

    fun reset() {
        _isEmergencyStop.value = false
        _isPaused.value = false
        consecutiveFailures = 0
    }

    /**
     * Checks if the agent is allowed to proceed.
     */
    fun shouldContinue(lastResult: TaskResult? = null, agentId: AgentId? = null): Boolean {
        if (_isEmergencyStop.value) {
            return false
        }

        // If paused, we return true to keep the GraphExecutor loop alive,
        // but the Executor must check isPaused to skip processing.
        if (_isPaused.value) {
            return true
        }

        // 1. Failure Check
        if (lastResult != null) {
            if (!lastResult.success) {
                consecutiveFailures++
            } else {
                consecutiveFailures = 0
            }
        }

        if (consecutiveFailures >= MAX_RETRIES) {
            // pauseAgent("Too many consecutive failures. Stuck loop detected.")
            return false
        }

        // 2. Loop Detection (Repetitive Actions)
        if (agentId != null) {
            val history = stateManager.getActionHistory(agentId)
            if (detectLoop(history)) {
                // Loop Detected: Pause and Request Intervention (Q40)
                _isPaused.value = true
                // We return true here to avoid killing the graph,
                // relying on the executor to handle the pause state.
                return true
            }
        }

        // 3. Battery Check
        // Policy: "Run to Death" (Design Interview Q41).
        // Agent continues regardless of battery level until the system shuts down.

        return true
    }

    /**
     * Detects if the agent is stuck in a loop.
     * Logic Compressed: simplified sliding window check.
     */
    internal fun detectLoop(history: List<AgentAction>): Boolean {
        val n = history.size
        if (n < 5) return false // optimization: early exit for short history

        // Check patterns of length 1..4
        for (L in 1..4) {
            val minRepeats = if (L == 1) 5 else 3
            val span = L * minRepeats
            if (n < span) continue

            // Check if the last span consists of 'minRepeats' identical blocks of length L
            // We compare the whole span against a generated sequence of the last block repeated
            // OR simpler: Compare block[i] with block[i-L] for the whole span range?
            // If history[i] == history[i-L], it implies repetition of period L.
            // We need to check this property for the range (n - span + L) until (n)

            // Logic Compression: A sequence repeats with period L if x[i] == x[i-L]
            // We check this property for the last (minRepeats-1) * L items.

            var isPeriodic = true
            // range to check: from (n - span + L) to (n - 1)
            // e.g. L=1, R=5, span=5. Check indices [n-4..n-1]. compare i with i-1.
            val checkStart = n - span + L
            for (i in checkStart until n) {
                if (history[i].description != history[i - L].description) {
                    isPeriodic = false
                    break
                }
            }

            if (isPeriodic) return true
        }
        return false
    }
}
