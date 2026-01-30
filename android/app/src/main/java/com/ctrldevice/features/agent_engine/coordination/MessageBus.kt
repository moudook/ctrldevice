package com.ctrldevice.features.agent_engine.coordination

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

sealed class Message {
    abstract val from: AgentId
    abstract val to: AgentId
    abstract val timestamp: Instant

    data class DataAvailable(
        override val from: AgentId,
        override val to: AgentId,
        override val timestamp: Instant,
        val key: String,
        val data: Any
    ) : Message()

    data class TaskComplete(
        override val from: AgentId,
        override val to: AgentId,
        override val timestamp: Instant,
        val taskId: TaskId,
        val result: TaskResult
    ) : Message()

    data class ResourcePreempted(
        override val from: AgentId,
        override val to: AgentId,
        override val timestamp: Instant,
        val resource: Resource,
        val reason: String
    ) : Message()

    data class ErrorOccurred(
        override val from: AgentId,
        override val to: AgentId,
        override val timestamp: Instant,
        val error: Throwable,
        val canRecover: Boolean
    ) : Message()

    data class StateCheckpoint(
        override val from: AgentId,
        override val to: AgentId,
        override val timestamp: Instant,
        val state: AgentState
    ) : Message()

    data class UserInterventionNeeded(
        override val from: AgentId,
        override val to: AgentId,
        override val timestamp: Instant,
        val reason: String
    ) : Message()
}

class MessageBus {
    companion object {
        val Instance = MessageBus()
    }

    private val channels = ConcurrentHashMap<AgentId, Channel<Message>>()

    private val MAX_LOG_SIZE = 500 // Q23: Bound memory usage

    // Optimization: Use SharedFlow for history management
    // 1. Thread-safe by design
    // 2. Automatic rolling window via replay and DROP_OLDEST
    // 3. Reactive - observers can subscribe to real-time updates
    private val _messageHistory = MutableSharedFlow<Message>(
        replay = MAX_LOG_SIZE,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messageHistory: SharedFlow<Message> = _messageHistory.asSharedFlow()

    fun register(agentId: AgentId): Channel<Message> {
        // Optimization: Use buffered channel (64) instead of UNLIMITED
        // This introduces backpressure: if an agent is too slow, the sender will suspend,
        // preventing OOM from infinite buffering.
        return Channel<Message>(capacity = 64, onBufferOverflow = BufferOverflow.SUSPEND).also {
            channels[agentId] = it
        }
    }

    fun unregister(agentId: AgentId) {
        channels.remove(agentId)?.close()
    }

    suspend fun send(message: Message) {
        // 1. Emit to history flow (non-blocking due to DROP_OLDEST)
        _messageHistory.emit(message)

        // 2. Route to destination (suspending if buffer full)
        channels[message.to]?.send(message)
    }

    // Returns a snapshot of the current history
    fun getHistory(): List<Message> = _messageHistory.replayCache
}
