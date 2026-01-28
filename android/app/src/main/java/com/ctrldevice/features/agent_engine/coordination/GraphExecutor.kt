package com.ctrldevice.features.agent_engine.coordination

import kotlinx.coroutines.*
import kotlinx.datetime.Clock

class GraphExecutor(
    private val graph: TaskGraph,
    private val resourceManager: ResourceManager,
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {
    private val activeAgents = mutableMapOf<TaskId, Job>()
    private val ORCHESTRATOR_ID = "Orchestrator"

    suspend fun execute() = coroutineScope {
        while (!graph.isComplete()) {
            val readyTasks = graph.getReadyTasks()

            readyTasks.forEach { task ->
                if (task.id !in activeAgents) {
                    val job = launch {
                        executeWithCoordination(task)
                    }
                    activeAgents[task.id] = job
                }
            }
            delay(500)
        }
    }

    private suspend fun executeWithCoordination(task: TaskNode) {
        if (task !is TaskNode.AtomicTask) return

        val agentId = "${task.assignedTo}_${task.id}"
        
        try {
            // 1. Acquire Resources
            val resources = determineRequiredResources(task)
            val leases = resources.mapNotNull { resource ->
                resourceManager.acquire(
                    resource = resource,
                    requester = agentId,
                    priority = task.priority,
                    timeout = task.estimatedDuration * 2
                )
            }

            if (leases.size < resources.size) {
                // Failed to get all resources, release what we got and retry later
                leases.forEach { resourceManager.release(it.resource, agentId) }
                return // Will be picked up again in next loop
            }

            // 2. Execute (Stubbed for now)
            // val result = agent.execute(task)
            val result = TaskResult.success() // Placeholder
            
            // 3. Complete
            graph.markCompleted(task.id, result)
            
            // 4. Release Resources
            leases.forEach { lease ->
                resourceManager.release(lease.resource, agentId)
            }

        } catch (e: Exception) {
            graph.markFailed(task.id, e)
            messageBus.send(
                Message.ErrorOccurred(
                    from = agentId,
                    to = ORCHESTRATOR_ID,
                    timestamp = Clock.System.now(),
                    error = e,
                    canRecover = true
                )
            )
        } finally {
            activeAgents.remove(task.id)
        }
    }

    private fun determineRequiredResources(task: TaskNode.AtomicTask): List<Resource> {
        return when (task.assignedTo) {
            AgentType.RESEARCH -> listOf(
                Resource.App("com.android.chrome"),
                Resource.Screen(isExclusive = true),
                Resource.Network(priority = task.priority)
            )
            AgentType.SOCIAL -> listOf(
                Resource.App("com.whatsapp"),
                Resource.Screen(isExclusive = true)
            )
            AgentType.MEDIA -> listOf(
                Resource.App("com.google.android.youtube"),
                Resource.Screen(isExclusive = false)
            )
            AgentType.SYSTEM -> listOf(
                Resource.Storage("/sdcard/Download")
            )
        }
    }
}
