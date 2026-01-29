package com.ctrldevice.features.agent_engine.coordination

import com.ctrldevice.features.agent_engine.safety.AgentGovernor
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

class GraphExecutor(
    private val graph: TaskGraph,
    private val resourceManager: ResourceManager,
    private val messageBus: MessageBus,
    private val stateManager: StateManager,
    private val agentGovernor: AgentGovernor
) {
    private val activeAgents = mutableMapOf<TaskId, Job>()
    private val ORCHESTRATOR_ID = "Orchestrator"

    suspend fun execute() = coroutineScope {
        while (!graph.isComplete()) {
            if (!agentGovernor.shouldContinue()) {
                messageBus.send(
                    Message.DataAvailable(
                        from = ORCHESTRATOR_ID,
                        to = "All",
                        timestamp = Clock.System.now(),
                        key = "status",
                        data = "EMERGENCY STOP TRIGGERED by Agent Governor."
                    )
                )
                break
            }

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

            // 2. Execute
            val agent = createAgent(task, agentId)
            val result = if (agent is com.ctrldevice.features.agent_engine.core.ExploratoryAgent) {
                // Pass the primary resource (e.g. Screen or App) to the loop
                // For simplicity in prototype, just pick the first one or a dummy one if empty
                val primaryResource = resources.firstOrNull() ?: Resource.Screen(true)
                agent.executeRalphLoop(task, primaryResource)
            } else {
                TaskResult.success() // Fallback for unimplemented agents
            }

            // 3. Complete
            if (result.success) {
                graph.markCompleted(task.id, result)
                agentGovernor.shouldContinue(result, agentId) // Report success to reset counters
            } else {
                 graph.markFailed(task.id, result.error ?: Exception("Unknown error"))
                 agentGovernor.shouldContinue(result, agentId) // Report failure to increment counters & check loops
            }
            
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

    private fun createAgent(task: TaskNode.AtomicTask, agentId: AgentId): com.ctrldevice.features.agent_engine.core.Agent? {
        return when (task.assignedTo) {
            AgentType.SYSTEM -> com.ctrldevice.features.agent_engine.core.SystemAgent(agentId, resourceManager, messageBus, stateManager)
            AgentType.RESEARCH -> com.ctrldevice.features.agent_engine.core.ResearchAgent(agentId, resourceManager, messageBus, stateManager)
            else -> null
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
