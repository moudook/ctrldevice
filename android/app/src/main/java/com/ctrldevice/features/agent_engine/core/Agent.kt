package com.ctrldevice.features.agent_engine.core

import com.ctrldevice.features.agent_engine.coordination.AgentId
import com.ctrldevice.features.agent_engine.coordination.MessageBus
import com.ctrldevice.features.agent_engine.coordination.Message
import com.ctrldevice.features.agent_engine.coordination.StateManager
import kotlinx.datetime.Clock

/**
 * Base Agent class.
 */
abstract class Agent(
    val id: AgentId,
    protected val messageBus: MessageBus,
    protected val stateManager: StateManager
) {
    protected suspend fun emit(message: Message) {
        messageBus.send(message)
    }
}
