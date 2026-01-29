package com.ctrldevice.features.agent_engine.safety

import com.ctrldevice.features.agent_engine.coordination.TaskResult
import com.ctrldevice.features.agent_engine.coordination.StateManager
import com.ctrldevice.features.agent_engine.coordination.AgentId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The "Circuit Breaker" to prevent runaway agents.
 * Monitors failures, resource usage, and explicit user stops.
 */
class AgentGovernor(
    private val stateManager: StateManager
) {

    private var consecutiveFailures = 0
    private val MAX_RETRIES = 5
    // private val HOURLY_DATA_LIMIT_MB = 100 // Future feature
    // private var dataUsedMb = 0.0

    private val _isEmergencyStop = MutableStateFlow(false)
    val isEmergencyStop = _isEmergencyStop.asStateFlow()

    fun triggerEmergencyStop() {
        _isEmergencyStop.value = true
    }

    fun reset() {
        _isEmergencyStop.value = false
        consecutiveFailures = 0
    }

    /**
     * Checks if the agent is allowed to proceed.
     */
    fun shouldContinue(lastResult: TaskResult? = null, agentId: AgentId? = null): Boolean {
        if (_isEmergencyStop.value) {
            return false
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
            if (history.size >= 5) {
                val lastAction = history.last().description
                val repeated = history.takeLast(5).all { it.description == lastAction }
                if (repeated) {
                    // Log logic here? For now just stop.
                    return false
                }
            }
        }

        // 3. Battery Check (Mocked for now, can integrate BatteryTool logic later)
        /*
        if (batteryLevel < 15) {
            pauseAgent("Battery critically low (<15%). Preserving power.")
            return false
        }
        */

        return true
    }
}
