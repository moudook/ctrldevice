package com.ctrldevice.features.agent_engine.coordination

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

typealias TaskId = String

enum class DataType { STRING, LIST, OBJECT }
enum class AgentType { RESEARCH, SOCIAL, MEDIA, SYSTEM }

sealed class TaskNode {
    abstract val id: TaskId
    abstract val priority: Int
    abstract val description: String

    data class AtomicTask(
        override val id: TaskId,
        override val priority: Int,
        override val description: String,
        val assignedTo: AgentType,
        val estimatedDuration: Duration
    ) : TaskNode()

    data class ParallelGroup(
        override val id: TaskId,
        override val priority: Int,
        override val description: String,
        val tasks: List<TaskNode>
    ) : TaskNode()

    data class SequentialGroup(
        override val id: TaskId,
        override val priority: Int,
        override val description: String,
        val tasks: List<TaskNode>
    ) : TaskNode()
}

data class TaskEdge(
    val from: TaskId,
    val to: TaskId,
    val dataFlow: DataRequirement?
)

data class DataRequirement(
    val key: String,
    val type: DataType,
    val optional: Boolean = false
)

data class TaskResult(
    val success: Boolean,
    val data: Map<String, Any> = emptyMap(),
    val error: Throwable? = null
) {
    companion object {
        fun success(data: Map<String, Any> = emptyMap()) = TaskResult(true, data)
        fun failure(error: Throwable) = TaskResult(false, error = error)
    }
}

class TaskGraph {
    private val nodes = mutableMapOf<TaskId, TaskNode>()
    private val edges = mutableMapOf<TaskId, MutableList<TaskEdge>>()
    private val completedTasks = mutableSetOf<TaskId>()
    private val failedTasks = mutableSetOf<TaskId>()
    private val taskResults = ConcurrentHashMap<TaskId, TaskResult>()

    fun addNode(node: TaskNode) {
        nodes[node.id] = node
    }

    fun addEdge(from: TaskId, to: TaskId, dataFlow: DataRequirement? = null) {
        edges.getOrPut(from) { mutableListOf() }.add(TaskEdge(from, to, dataFlow))
    }

    fun getReadyTasks(): List<TaskNode> {
        return nodes.values.filter { node ->
            node.id !in completedTasks &&
            node.id !in failedTasks &&
            areDependenciesSatisfied(node.id)
        }
    }

    private fun areDependenciesSatisfied(taskId: TaskId): Boolean {
        val dependencies = edges.entries
            .filter { it.value.any { edge -> edge.to == taskId } }
            .map { it.key }

        return dependencies.all { depId ->
            depId in completedTasks ||
            (edges[depId]?.any { it.dataFlow?.optional == true } == true && depId in failedTasks)
        }
    }

    fun markCompleted(taskId: TaskId, result: TaskResult) {
        completedTasks.add(taskId)
        taskResults[taskId] = result
    }

    fun markFailed(taskId: TaskId, error: Throwable) {
        failedTasks.add(taskId)
    }

    fun isComplete(): Boolean {
        return nodes.keys.all { it in completedTasks || it in failedTasks }
    }

    fun addCompositeNode(node: TaskNode) {
        when (node) {
            is TaskNode.AtomicTask -> addNode(node)
            is TaskNode.ParallelGroup -> {
                node.tasks.forEach { addCompositeNode(it) }
            }
            is TaskNode.SequentialGroup -> {
                if (node.tasks.isEmpty()) return

                // 1. Add all children
                node.tasks.forEach { addCompositeNode(it) }

                // 2. Link them
                for (i in 0 until node.tasks.size - 1) {
                    val current = node.tasks[i]
                    val next = node.tasks[i+1]

                    val sinks = getSinkIds(current)
                    val sources = getSourceIds(next)

                    for (sink in sinks) {
                        for (source in sources) {
                            addEdge(sink, source)
                        }
                    }
                }
            }
        }
    }

    private fun getSinkIds(node: TaskNode): List<TaskId> {
        return when (node) {
            is TaskNode.AtomicTask -> listOf(node.id)
            is TaskNode.ParallelGroup -> node.tasks.flatMap { getSinkIds(it) }
            is TaskNode.SequentialGroup -> if (node.tasks.isNotEmpty()) getSinkIds(node.tasks.last()) else emptyList()
        }
    }

    private fun getSourceIds(node: TaskNode): List<TaskId> {
        return when (node) {
            is TaskNode.AtomicTask -> listOf(node.id)
            is TaskNode.ParallelGroup -> node.tasks.flatMap { getSourceIds(it) }
            is TaskNode.SequentialGroup -> if (node.tasks.isNotEmpty()) getSourceIds(node.tasks.first()) else emptyList()
        }
    }

    fun toMermaidDiagram(): String {
        return buildString {
            appendLine("graph TD")
            nodes.forEach { (id, node) ->
                val status = when {
                    id in completedTasks -> "✅"
                    id in failedTasks -> "❌"
                    else -> "⏳"
                }
                appendLine("    $id[$status ${node.description}]")
            }
            edges.forEach { (from, edgeList) ->
                edgeList.forEach { edge ->
                    val label = edge.dataFlow?.key ?: ""
                    appendLine("    $from -->|$label| ${edge.to}")
                }
            }
        }
    }
}
