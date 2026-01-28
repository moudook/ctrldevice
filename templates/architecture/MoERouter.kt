package com.ctrldevice.features.agent_engine.moe

import com.ctrldevice.domain.models.Task
import com.ctrldevice.features.agent_engine.swarm.SubAgent

/**
 * Mixture of Experts (MoE) Router.
 * Decides which agent(s) should handle a specific sub-task.
 */
class MoERouter(
    private val agents: List<SubAgent>
) {

    /**
     * Routes the task to the best expert.
     * @param task The sub-task to be executed.
     * @return The best suited Agent + A Confidence Score.
     */
    fun route(task: Task): Pair<SubAgent, Float> {
        // 1. Embedding Match: Compare Task Embedding with Agent Description Embedding.
        val scores = agents.map { agent ->
            agent to calculateSimilarity(task.description, agent.capabilities)
        }
        
        // 2. Heuristic Check: Does the agent have the required tools?
        // e.g., Task needs "Chrome", but SocialAgent only has "WhatsApp".
        val validAgents = scores.filter { (agent, _) -> 
            agent.hasToolsFor(task.requiredTools)
        }
        
        // 3. Return top match
        return validAgents.maxByOrNull { it.second } ?: throw NoExpertFoundException()
    }

    private fun calculateSimilarity(taskDesc: String, agentCaps: String): Float {
        // Placeholder for Vector Similarity Search (Cosine Similarity)
        return 0.9f 
    }
}
