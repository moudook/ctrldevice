package com.ctrldevice.features.agent_engine.safety

import com.ctrldevice.domain.models.ActionResult

/**
 * The "Circuit Breaker" to prevent runaway agents.
 */
class AgentGovernor {

    private var consecutiveFailures = 0
    private val MAX_RETRIES = 5
    private val HOURLY_DATA_LIMIT_MB = 100
    private var dataUsedMb = 0.0

    /**
     * Checks if the agent is allowed to proceed.
     */
    fun shouldContinue(lastResult: ActionResult, batteryLevel: Int): Boolean {
        
        // 1. Failure Check
        if (!lastResult.success) {
            consecutiveFailures++
        } else {
            consecutiveFailures = 0
        }

        if (consecutiveFailures >= MAX_RETRIES) {
            pauseAgent("Too many consecutive failures. Stuck loop detected.")
            return false
        }

        // 2. Battery Check
        if (batteryLevel < 15) {
            pauseAgent("Battery critically low (<15%). Preserving power.")
            return false
        }

        // 3. Data Check
        if (dataUsedMb > HOURLY_DATA_LIMIT_MB) {
            pauseAgent("Hourly data limit exceeded.")
            return false
        }

        return true
    }

    private fun pauseAgent(reason: String) {
        // Signal Orchestrator to PAUSE
        println("GOVERNOR STOP: $reason")
    }
}
