package com.ctrldevice.features.agent_engine.core

import com.ctrldevice.features.agent_engine.coordination.*
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * The "Worker" implementation implementing the Ralph Loop:
 * Try -> Fail -> Observe -> Reflect -> Pivot -> Retry.
 */
abstract class ExploratoryAgent(
    id: AgentId,
    private val resourceManager: ResourceManager,
    messageBus: MessageBus,
    stateManager: StateManager
) : Agent(id, messageBus, stateManager) {

    suspend fun executeRalphLoop(
        task: TaskNode.AtomicTask,
        requiredResource: Resource
    ): TaskResult {

        // 1. Acquire Resource (The "Bouncer" check)
        val lease = resourceManager.acquire(
            resource = requiredResource,
            requester = id,
            priority = task.priority,
            timeout = task.estimatedDuration * 2
        ) ?: return TaskResult.failure(Exception("Could not acquire $requiredResource"))

        try {
            var attempts = 0
            val maxRetries = 5 // Increased to allow for Scroll/Wait/Back strategies
            var currentStrategy = "DEFAULT"

            while (attempts < maxRetries) {
                // A. ACT
                val success = performAction(currentStrategy, task)

                // B. OBSERVE (Verification)
                val verified = verifyOutcome(task)

                if (success && verified) {
                    emit(Message.DataAvailable(
                        from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                        key = "success", data = "Action Successful and Verified."
                    ))
                    return TaskResult.success()
                }

                // C. REFLECT & PIVOT
                // "Strategy A failed. Let's try Strategy B."
                currentStrategy = pivotStrategy(currentStrategy, attempts)
                attempts++

                // Publish status for debugging
                emit(Message.DataAvailable(
                    from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                    key = "log", data = "Attempt $attempts failed. Pivoting to $currentStrategy"
                ))
            }

            return TaskResult.failure(Exception("Max retries reached in Ralph Loop"))

        } finally {
            // ALWAYS release resources
            resourceManager.release(lease.resource, id)
        }
    }

    abstract suspend fun performAction(strategy: String, task: TaskNode.AtomicTask): Boolean
    abstract suspend fun verifyOutcome(task: TaskNode.AtomicTask): Boolean
    abstract fun pivotStrategy(current: String, attempt: Int): String
}
