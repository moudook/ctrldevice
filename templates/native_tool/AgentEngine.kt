package com.ctrldevice.features.agent_engine.core

import com.ctrldevice.features.agent_engine.coordination.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The Main Entry Point (The "Kernel").
 * Initializes the Agent OS components.
 */
class CtrlDeviceCore {

    // 1. The Nervous System
    val messageBus = MessageBus()
    
    // 2. The Persistence Layer
    val stateManager = StateManager()
    
    // 3. The Resource Arbiter
    val resourceManager = ResourceManager(messageBus, stateManager)
    
    // 4. The Orchestrator (Planner)
    val orchestrator = Orchestrator(resourceManager)

    fun startTask(userInstruction: String) {
        CoroutineScope(Dispatchers.Default).launch {
            // A. Plan
            val taskGraph = orchestrator.plan(userInstruction)
            
            // B. Execute
            val executor = GraphExecutor(
                graph = taskGraph,
                resourceManager = resourceManager,
                messageBus = messageBus,
                stateManager = stateManager
            )
            
            executor.execute()
        }
    }
}

class Orchestrator(private val resourceManager: ResourceManager) {
    fun plan(instruction: String): TaskGraph {
        // TODO: Call LLM to generate DAG
        return TaskGraph() 
    }
}