package com.ctrldevice.features.agent_engine.coordination

import kotlinx.coroutines.channels.Channel
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
}

class MessageBus {
    private val channels = ConcurrentHashMap<AgentId, Channel<Message>>()
    private val messageLog = mutableListOf<Message>()

    fun register(agentId: AgentId): Channel<Message> {
        return Channel<Message>(Channel.UNLIMITED).also {
            channels[agentId] = it
        }
    }

    fun unregister(agentId: AgentId) {
        channels.remove(agentId)?.close()
    }

    suspend fun send(message: Message) {
        messageLog.add(message)
        channels[message.to]?.send(message)
    }

    fun getHistory(): List<Message> = messageLog.toList()
}
