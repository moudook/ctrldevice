package com.ctrldevice.features.agent_engine.coordination

import com.ctrldevice.features.agent_engine.parsing.CommandIntent
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
        val estimatedDuration: Duration,
        val scheduledStart: Long = 0L, // Timestamp in ms. 0 = Immediately.
        val intent: CommandIntent? = null // Optimization: Pre-parsed intent to avoid re-parsing
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
    // Optimization: Reverse edge lookup for O(1) dependency checking
    private val reverseEdges = mutableMapOf<TaskId, MutableList<TaskId>>()

    // Optimization: Maintain a live set of ready tasks to avoid O(N) scanning
    // Kahn's Algorithm style: Ready tasks are those with satisfied dependencies.
    private val readyTaskIds = ConcurrentHashMap.newKeySet<TaskId>()

    private val completedTasks = mutableSetOf<TaskId>()
    private val failedTasks = mutableSetOf<TaskId>()
    private val taskResults = ConcurrentHashMap<TaskId, TaskResult>()

    fun addNode(node: TaskNode) {
        nodes[node.id] = node
        // Newly added node is ready by default until dependencies are added
        readyTaskIds.add(node.id)
    }

    fun addEdge(from: TaskId, to: TaskId, dataFlow: DataRequirement? = null) {
        edges.getOrPut(from) { mutableListOf() }.add(TaskEdge(from, to, dataFlow))
        reverseEdges.getOrPut(to) { mutableListOf() }.add(from)

        // Re-evaluate 'to' node's readiness
        if (areDependenciesSatisfied(to)) {
            if (to !in completedTasks && to !in failedTasks) {
                readyTaskIds.add(to)
            }
        } else {
            readyTaskIds.remove(to)
        }
    }

    fun getReadyTasks(): List<TaskNode> {
        // O(1) retrieval of pre-calculated set
        return readyTaskIds.mapNotNull { nodes[it] }
    }

    private fun areDependenciesSatisfied(taskId: TaskId): Boolean {
        // Optimization: Use reverse map instead of scanning all edges
        val dependencies = reverseEdges[taskId] ?: return true

        return dependencies.all { depId ->
            depId in completedTasks ||
            (edges[depId]?.any { it.dataFlow?.optional == true } == true && depId in failedTasks)
        }
    }

    fun markCompleted(taskId: TaskId, result: TaskResult) {
        completedTasks.add(taskId)
        taskResults[taskId] = result
        readyTaskIds.remove(taskId)
        updateDependents(taskId)
    }

    fun markFailed(taskId: TaskId, error: Throwable) {
        failedTasks.add(taskId)
        readyTaskIds.remove(taskId)
        // Even on failure, optional dependencies might be satisfied
        updateDependents(taskId)
    }

    /**
     * Retrieves aggregated data from all satisfied dependencies of the given task.
     * Uses the DataRequirement definitions on edges to map outputs to inputs.
     */
    fun getDataFromDependencies(taskId: TaskId): Map<String, Any> {
        val inputData = mutableMapOf<String, Any>()
        val dependencies = reverseEdges[taskId] ?: return emptyMap()

        dependencies.forEach { depId ->
            val result = taskResults[depId] ?: return@forEach
            if (!result.success) return@forEach

            // Find the edge definition to see what data was requested
            val edge = edges[depId]?.find { it.to == taskId }
            val req = edge?.dataFlow

            if (req != null) {
                // If specific key requested
                val value = result.data[req.key]
                if (value != null) {
                    inputData[req.key] = value
                }
            } else {
                // Default: Merge all data if no specific requirement (simplification)
                // Or maybe we shouldn't merge blindly?
                // Let's adopt a convention: merge all "output" into "input" if no mapping exists.
                inputData.putAll(result.data)
            }
        }
        return inputData
    }

    private fun updateDependents(finishedTaskId: TaskId) {
        // Check all children to see if they are now ready
        val children = edges[finishedTaskId]?.map { it.to } ?: emptyList()
        children.forEach { childId ->
             if (childId !in completedTasks && childId !in failedTasks) {
                 if (areDependenciesSatisfied(childId)) {
                     readyTaskIds.add(childId)
                 }
             }
        }
    }

    fun isComplete(): Boolean {
        return nodes.keys.all { it in completedTasks || it in failedTasks }
    }

    data class GraphSnapshot(
        val nodes: Map<TaskId, TaskNode>,
        val edges: Map<TaskId, List<TaskEdge>>,
        val completed: Set<TaskId>,
        val failed: Set<TaskId>
    )

    fun getSnapshot(): GraphSnapshot {
        return GraphSnapshot(
            nodes.toMap(),
            edges.mapValues { it.value.toList() },
            completedTasks.toSet(),
            failedTasks.toSet()
        )
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
