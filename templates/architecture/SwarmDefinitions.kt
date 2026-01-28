package com.ctrldevice.features.agent_engine.core

import com.ctrldevice.features.agent_engine.coordination.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

/**
 * Base class for all Swarm Agents.
 * Enforces MessageBus communication (No direct calls).
 */
abstract class Agent(
    val id: AgentId,
    private val messageBus: MessageBus
) {
    private val inbox = messageBus.register(id)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            for (msg in inbox) {
                handleMessage(msg)
            }
        }
    }

    protected open suspend fun handleMessage(message: Message) {
        when (message) {
            is Message.ResourcePreempted -> onPreempted(message)
            is Message.ErrorOccurred -> onError(message)
            else -> {} // Ignore unknown messages
        }
    }

    private fun onPreempted(msg: Message.ResourcePreempted) {
        // Stop work immediately, state is already saved by StateManager
        scope.coroutineContext.cancelChildren()
    }

    private fun onError(msg: Message.ErrorOccurred) {
        // Log or react to upstream errors
    }

    protected suspend fun emit(msg: Message) {
        messageBus.send(msg)
    }

    fun cleanup() {
        messageBus.unregister(id)
        scope.cancel()
    }
}