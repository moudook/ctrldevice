package com.ctrldevice.features.agent_engine.coordination

import com.ctrldevice.features.agent_engine.config.AppConfig
import com.ctrldevice.features.agent_engine.core.Agent
import com.ctrldevice.features.agent_engine.core.ResearchAgent
import com.ctrldevice.features.agent_engine.core.SystemAgent
import com.ctrldevice.features.agent_engine.intelligence.LLMBrain
import com.ctrldevice.features.agent_engine.intelligence.RuleBasedBrain

/**
 * Factory for creating Agent instances with the appropriate Brain configuration.
 */
class AgentFactory(
    private val appConfig: AppConfig,
    private val resourceManager: ResourceManager,
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {
    fun createAgent(task: TaskNode.AtomicTask, agentId: AgentId): Agent? {
        val brain = when (appConfig.brainType) {
            AppConfig.BRAIN_LLM_REMOTE, AppConfig.BRAIN_LLM_LOCAL -> {
                LLMBrain(appConfig.llmApiKey, appConfig.llmEndpoint, appConfig.llmModel)
            }
            else -> RuleBasedBrain()
        }

        return when (task.assignedTo) {
            AgentType.SYSTEM -> SystemAgent(agentId, resourceManager, messageBus, stateManager, brain)
            AgentType.RESEARCH -> ResearchAgent(agentId, resourceManager, messageBus, stateManager, brain)
            else -> null
        }
    }
}
