package com.ctrldevice.features.agent_engine.skills

import com.ctrldevice.features.agent_engine.coordination.AgentType
import com.ctrldevice.features.agent_engine.coordination.TaskNode
import com.ctrldevice.features.agent_engine.parsing.CommandIntent
import com.ctrldevice.features.agent_engine.parsing.IntentParser
import kotlin.time.Duration.Companion.seconds

/**
 * Represents a reusable sequence of actions (Macro).
 * Encapsulates the logic to convert itself into a TaskNode graph.
 */
data class Macro(
    val name: String,
    val description: String = "",
    val triggers: List<String> = emptyList(), // Keywords that trigger this macro
    val commands: List<String> = emptyList(), // Sequential commands to execute (JSON source)
    @Transient val template: ((String) -> TaskNode)? = null // Optional function (Code source)
) {
    fun generateTask(id: String): TaskNode? {
        // 1. Prefer Code Template
        if (template != null) {
            return template.invoke(id)
        }

        // 2. Fallback to JSON Commands
        if (commands.isNotEmpty()) {
            val tasks = mutableListOf<TaskNode>()
            commands.forEachIndexed { index, cmd ->
                // We need to parse the command string into an intent, then to a task.
                // Since we don't have access to CommandParser here to avoid circular dependency,
                // we'll do a lightweight construction using IntentParser directly.
                val intent = IntentParser.parse(cmd)
                val subId = "${id}_$index"

                // Construct atomic task directly (Logic Compression: skip CommandParser recursion for simple macros)
                val taskDescription = when(intent) {
                    is CommandIntent.Click -> "Click '${intent.target}'"
                    is CommandIntent.Input -> "Type '${intent.text}'"
                    is CommandIntent.GlobalAction -> intent.action.name
                    else -> cmd
                }

                tasks.add(TaskNode.AtomicTask(
                    id = subId,
                    priority = 20, // System Priority
                    description = taskDescription,
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds,
                    intent = intent
                ))
            }

            return TaskNode.SequentialGroup(
                id = id,
                priority = 20,
                description = description.ifEmpty { name },
                tasks = tasks
            )
        }
        return null
    }
}
