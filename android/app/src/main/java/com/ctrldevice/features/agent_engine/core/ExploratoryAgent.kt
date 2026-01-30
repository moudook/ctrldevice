package com.ctrldevice.features.agent_engine.core

import com.ctrldevice.features.agent_engine.coordination.*
import com.ctrldevice.agent.tools.ToolRegistry
import com.ctrldevice.features.agent_engine.intelligence.AgentBrain
import com.ctrldevice.features.agent_engine.strategies.RecoveryStrategy
import android.util.Log
import kotlinx.datetime.Clock
import kotlinx.coroutines.delay

/**
 * The "Worker" implementation implementing the Ralph Loop:
 * Try -> Fail -> Observe -> Reflect -> Pivot -> Retry.
 */
abstract class ExploratoryAgent(
    id: AgentId,
    private val resourceManager: ResourceManager,
    messageBus: MessageBus,
    stateManager: StateManager,
    protected val brain: AgentBrain
) : Agent(id, messageBus, stateManager) {

    suspend fun executeRalphLoop(
        task: TaskNode.AtomicTask,
        requiredResource: Resource,
        inputData: Map<String, Any> = emptyMap()
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
            var currentStrategy: RecoveryStrategy = RecoveryStrategy.None

            // Optimization: Cache context to prevent redundant reads between Verify (T) and Sense (T+1)
            var cachedScreenContext: String? = null
            var cachedScreenshot: String? = null

            while (attempts < maxRetries) {

                // 1. SENSE: Get environment state (reuse if fresh from verification)
                if (cachedScreenContext == null) {
                    val (ctx, shot) = senseEnvironment()
                    cachedScreenContext = ctx
                    cachedScreenshot = shot
                }

                // 2. ACT
                // Smart cast handles nullability as it was initialized above if null
                val success = performAction(currentStrategy, task, inputData, cachedScreenContext!!, cachedScreenshot)

                // Invalidating cache because action likely changed the screen
                cachedScreenContext = null
                cachedScreenshot = null

                // 3. OBSERVE (Verification)
                // Returns the fresh context it read during verification
                val (verified, newContext) = verifyOutcome(task)

                // Update cache with the fresh context from verification
                if (newContext != null) {
                    cachedScreenContext = newContext
                }

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
                    key = "log", data = "Attempt $attempts failed. Pivoting to ${currentStrategy.name}"
                ))
            }

            return TaskResult.failure(Exception("Max retries reached in Ralph Loop"))

        } finally {
            // ALWAYS release resources
            resourceManager.release(lease.resource, id)
        }
    }

    /**
     * Executes a single attempt to perform the task.
     * Follows the "Sense -> Think -> Act" cycle.
     */
    protected open suspend fun performAction(
        strategy: RecoveryStrategy,
        task: TaskNode.AtomicTask,
        inputData: Map<String, Any>,
        screenContext: String,
        screenshotBase64: String?
    ): Boolean {
        Log.d("ExploratoryAgent", "Performing action for task: ${task.description} with strategy: ${strategy.name}")

        // 1. Execute Recovery Strategy (Hook)
        executeRecoveryStrategy(strategy)

        // 2. Gather Context (Sense) - Now passed in
        // val (screenContext, screenshotBase64) = senseEnvironment()

        val history = stateManager.getActionHistory(id).map { it.description }

        // 3. Consult the Brain (Think)
        val thought = brain.proposeNextStep(task, screenContext, history, screenshotBase64, inputData)

        emit(Message.DataAvailable(
            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
            key = "thought", data = "Brain Reasoning: ${thought.reasoning}"
        ))

        // 4. Execute Tool (Act)
        val tool = ToolRegistry.getTool(thought.toolName)

        if (tool != null) {
            val params = thought.toolParams

            emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "action", data = "Executing ${tool.name} with params: '$params'..."
            ))

            // Log to Memory
            stateManager.logAction(id, "Executed ${tool.name} with params: $params (Strategy: ${strategy.name})")

            val result = tool.execute(params)
            Log.d("ExploratoryAgent", "Tool result: $result")

            emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "observation", data = "Tool Output: ${result.output}"
            ))

            return result.success
        } else {
            emit(Message.DataAvailable(
                from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                key = "thought", data = "Brain proposed unknown tool: ${thought.toolName}"
            ))
            return false
        }
    }

    /**
     * Senses the environment (Screen text + Screenshot).
     * Can be overridden if agents need different sensory inputs.
     */
    protected open suspend fun senseEnvironment(): Pair<String, String?> {
        var screenContext = ""
        var screenshotBase64: String? = null

        // Read Screen
        val screenReader = ToolRegistry.getTool("read_screen")
        if (screenReader != null) {
            // Simple retry logic for reading screen could go here or be in the tool
            val result = screenReader.execute("{}")
            if (result.success && result.output.isNotBlank()) {
                screenContext = result.output
            }
        }

        // Capture Screenshot
        val screenshotTool = ToolRegistry.getTool("take_screenshot")
        if (screenshotTool != null) {
            val shotResult = screenshotTool.execute("{}")
            if (shotResult.success) {
                screenshotBase64 = shotResult.output
                // Optional: Persist screenshot here if needed, or leave to StateManager in future
                // screenshotBase64 is guaranteed non-null from tool output
                if (screenshotBase64 != null) {
                    val screenshotId = stateManager.saveScreenshot(screenshotBase64)
                    if (screenshotId != null) {
                        emit(Message.DataAvailable(
                            from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                            key = "log", data = "Saved training screenshot: $screenshotId"
                        ))
                    }
                }
            }
        }
        return Pair(screenContext, screenshotBase64)
    }

    /**
     * Logic Compression: Generic Polling Verification Loop.
     * Replaces duplicate loops in subclasses.
     */
    protected suspend fun pollVerification(
        timeoutMs: Long,
        checkExpectations: (String) -> Boolean
    ): Pair<Boolean, String?> {
        val screenReader = ToolRegistry.getTool("read_screen") ?: return Pair(false, null)
        val startTime = System.currentTimeMillis()
        var lastContent: String? = null

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val result = screenReader.execute("{}")
            if (result.success) {
                val content = result.output
                lastContent = content
                if (checkExpectations(content)) {
                    return Pair(true, content)
                }
            }
            delay(500)
        }
        return Pair(false, lastContent)
    }

    /**
     * Executes the specific recovery strategy actions (e.g. scroll, wait).
     * Hoisted from SystemAgent to share across all exploratory agents.
     */
    protected open suspend fun executeRecoveryStrategy(strategy: RecoveryStrategy) {
        when (strategy) {
            is RecoveryStrategy.ScrollDown -> {
                emit(Message.DataAvailable(
                    from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                    key = "action", data = "Recovery: Scrolling down to find element..."
                ))
                val scrollTool = ToolRegistry.getTool("scroll")
                if (scrollTool != null) {
                    scrollTool.execute("down")
                    delay(1000) // Wait for scroll animation
                }
            }
            is RecoveryStrategy.Wait -> {
                emit(Message.DataAvailable(
                    from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                    key = "action", data = "Recovery: Waiting for UI to load..."
                ))
                delay(3000)
            }
            is RecoveryStrategy.Back -> {
                emit(Message.DataAvailable(
                    from = id, to = "Orchestrator", timestamp = Clock.System.now(),
                    key = "action", data = "Recovery: Going back to previous screen..."
                ))
                val backTool = ToolRegistry.getTool("go_back")
                if (backTool != null) {
                    backTool.execute("{}")
                    delay(1500) // Wait for transition
                }
            }
            else -> { /* Do nothing for None or Retry */ }
        }
    }

    abstract suspend fun verifyOutcome(task: TaskNode.AtomicTask): Pair<Boolean, String?>

    // Logic Compression: Table-Driven Strategy Selection
    protected abstract val recoveryStrategies: List<RecoveryStrategy>

    protected open fun pivotStrategy(current: RecoveryStrategy, attempt: Int): RecoveryStrategy {
        // Logic Compression: O(1) lookup instead of O(N) if/else branches
        return recoveryStrategies.getOrNull(attempt) ?: RecoveryStrategy.Retry
    }
}
