package com.ctrldevice.features.agent_engine.coordination

import com.ctrldevice.features.agent_engine.safety.AgentGovernor
import com.ctrldevice.features.agent_engine.config.AppConfig
import com.ctrldevice.features.agent_engine.intelligence.LLMBrain
import com.ctrldevice.features.agent_engine.intelligence.RuleBasedBrain
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

class GraphExecutor(
    private val graph: TaskGraph,
    private val resourceManager: ResourceManager,
    private val messageBus: MessageBus,
    private val stateManager: StateManager,
    private val agentGovernor: AgentGovernor,
    private val appConfig: AppConfig
) {
    // Optimization: ConcurrentHashMap to prevent race conditions between main loop and agent coroutines
    private val activeAgents = java.util.concurrent.ConcurrentHashMap<TaskId, Job>()
    private val updateSignal = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val ORCHESTRATOR_ID = "Orchestrator"
    private var totalStepsExecuted = 0
    private val MAX_TOTAL_STEPS = 50 // Q50: Hard Limit on reasoning steps

    // Architectural Decoupling: internal composition of factory and strategy
    private val agentFactory = AgentFactory(appConfig, resourceManager, messageBus, stateManager)
    private val resourceStrategy = ResourceStrategy()

    suspend fun execute() = coroutineScope {
        // Register for messages (Preemption, etc)
        val messageChannel = messageBus.register(ORCHESTRATOR_ID)

        try {
            // Monitor Messages
            val messageJob = launch {
                for (msg in messageChannel) {
                    if (msg is Message.ResourcePreempted) {
                        val victimAgentId = msg.to

                        // Find the corresponding task
                        // We iterate active tasks to find which one matches the agentId
                        val snapshot = graph.getSnapshot()
                        val victimEntry = activeAgents.entries.find { (taskId, _) ->
                            val node = snapshot.nodes[taskId] as? TaskNode.AtomicTask
                            val agentId = if (node != null) "${node.assignedTo}_${node.id}" else ""
                            agentId == victimAgentId
                        }

                        if (victimEntry != null) {
                            val (taskId, job) = victimEntry
                            // Cancel the job. This will throw CancellationException in executeWithCoordination
                            job.cancel(CancellationException("Preempted by ResourceManager"))
                            // cleanup happens in finally block of executeWithCoordination
                        }
                    }
                }
            }

            // Monitor Pause State
            val pauseJob = launch {
                agentGovernor.isPaused.collect { paused ->
                    if (paused) {
                        messageBus.send(
                            Message.UserInterventionNeeded(
                                from = ORCHESTRATOR_ID,
                                to = "User",
                                timestamp = Clock.System.now(),
                                reason = "Loop detected. Agent appears stuck."
                            )
                        )
                    }
                    // Trigger loop re-evaluation when pause state changes
                    updateSignal.trySend(Unit)
                }
            }

            while (!graph.isComplete()) {
            // Check Global Step Limit (Q50)
            if (totalStepsExecuted >= MAX_TOTAL_STEPS) {
                 messageBus.send(
                    Message.DataAvailable(
                        from = ORCHESTRATOR_ID,
                        to = "All",
                        timestamp = Clock.System.now(),
                        key = "status",
                        data = "HARD LIMIT REACHED ($MAX_TOTAL_STEPS steps). Stopping."
                    )
                )
                break
            }

            if (agentGovernor.isPaused.value) {
                // Wait for unpause signal
                updateSignal.receive()
                continue
            }

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
            var tasksLaunched = 0

            readyTasks.forEach { task ->
                // Check Schedule for AtomicTasks
                if (task is TaskNode.AtomicTask && task.scheduledStart > System.currentTimeMillis()) {
                    // Task is topologically ready but scheduled for the future. Skip for now.
                    return@forEach
                }

                if (!activeAgents.containsKey(task.id)) {
                    val job = launch {
                        executeWithCoordination(task)
                    }
                    activeAgents[task.id] = job
                    tasksLaunched++
                }
            }

            // Optimization: Event-driven wait instead of polling
            // We wait if:
            // 1. Tasks are running (wait for completion)
            // 2. No tasks running but graph incomplete (wait for schedule/timeout)
            if (activeAgents.isNotEmpty()) {
                // Wait for a task to finish OR 500ms heartbeat
                withTimeoutOrNull(500) {
                    updateSignal.receive()
                }
            } else if (tasksLaunched == 0 && !graph.isComplete()) {
                // Nothing running, nothing ready (likely waiting on schedule)
                delay(100)
            }
        }
    } finally {
        // Optimization: Ensure we unregister to prevent memory leaks if cancelled
        messageBus.unregister(ORCHESTRATOR_ID)
    }
}

    private suspend fun executeWithCoordination(task: TaskNode) {
        if (task !is TaskNode.AtomicTask) return

        val agentId = "${task.assignedTo}_${task.id}"

        // Logic Compression: `withResources` scope handles acquisition/release boilerplate
        try {
            withResources(task, agentId) { resources ->
                // 2. Execute
                val agent = agentFactory.createAgent(task, agentId)

                val result = if (agent is com.ctrldevice.features.agent_engine.core.ExploratoryAgent) {
                    val primaryResource = resources.firstOrNull() ?: Resource.Screen(true)
                    // Optimization: Data Flow Pipe
                    val inputData = graph.getDataFromDependencies(task.id)
                    agent.executeRalphLoop(task, primaryResource, inputData)
                } else {
                    TaskResult.success() // Fallback
                }

                // 3. Complete
                if (result.success) {
                    graph.markCompleted(task.id, result)
                } else {
                    graph.markFailed(task.id, result.error ?: Exception("Unknown error"))
                }

                // Update counters
                agentGovernor.shouldContinue(result, agentId)
                totalStepsExecuted++
                updateSignal.trySend(Unit)
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

    /**
     * Logic Compression: Higher-order function to encapsulate resource lifecycle.
     * Returns true if resources were acquired and block executed, false otherwise.
     */
    private suspend fun withResources(
        task: TaskNode.AtomicTask,
        agentId: String,
        block: suspend (List<Resource>) -> Unit
    ) {
        val resources = resourceStrategy.determineRequiredResources(task)
        val leases = ArrayList<ResourceLease>(resources.size)

        try {
            // Acquire all
            for (res in resources) {
                val lease = resourceManager.acquire(
                    resource = res,
                    requester = agentId,
                    priority = task.priority,
                    timeout = task.estimatedDuration * 2
                )
                if (lease != null) leases.add(lease)
                else break // Failed to get one
            }

            if (leases.size == resources.size) {
                block(resources)
            } else {
                // Failed to acquire all - loop will retry naturally via GraphExecutor logic
            }
        } finally {
            // Release all
            leases.forEach { resourceManager.release(it.resource, agentId) }
        }
    }
}
