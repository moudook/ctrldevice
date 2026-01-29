package com.ctrldevice.features.agent_engine.intelligence

import com.ctrldevice.features.agent_engine.coordination.TaskNode

/**
 * The "Ghost" in the Shell.
 * Responsible for high-level reasoning, planning, and tool selection.
 *
 * Implementations can be:
 * - RuleBasedBrain (Regex/Heuristics) - Current Prototype
 * - LocalLLMBrain (Gemma/Llama on device)
 * - CloudLLMBrain (Claude/GPT via API)
 */
interface AgentBrain {

    /**
     * Decides the next action based on the task and current context.
     * @param task The high-level goal.
     * @param screenContext Text representation of the screen (Proprioception).
     * @param previousActions List of actions already taken (Memory).
     * @return A plan containing the tool to use and its parameters.
     */
    suspend fun proposeNextStep(
        task: TaskNode.AtomicTask,
        screenContext: String,
        previousActions: List<String>
    ): BrainThought
}

data class BrainThought(
    val reasoning: String, // "I see a 'Settings' button, so I should click it."
    val toolName: String,  // "click_element"
    val toolParams: String // "Settings"
)
