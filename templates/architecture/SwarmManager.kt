package com.ctrldevice.features.agent_engine.swarm

import com.ctrldevice.features.agent_engine.coordination.*

/**
 * High-level facade for managing the Swarm.
 * Wraps the Kernel components (GraphExecutor, ResourceManager) for easier usage.
 */
class SwarmManager(
    private val orchestrator: Orchestrator,
    private val resourceManager: ResourceManager,
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {

    /**
     * The main entry point for the User to start a mission.
     */
    suspend fun deploySwarm(missionGoal: String) {
        // 1. Plan
        val taskGraph = orchestrator.plan(missionGoal)
        
        // 2. Execute
        val executor = GraphExecutor(
            graph = taskGraph,
            resourceManager = resourceManager,
            messageBus = messageBus,
            stateManager = stateManager
        )
        
        executor.execute()
    }

    /**
     * Emergency Stop (e.g., User touched screen).
     */
    fun haltSwarm() {
        // TODO: Implement cancellation logic in GraphExecutor
    }
}