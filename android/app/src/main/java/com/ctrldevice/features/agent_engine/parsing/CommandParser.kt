package com.ctrldevice.features.agent_engine.parsing

import com.ctrldevice.features.agent_engine.coordination.AgentType
import com.ctrldevice.features.agent_engine.coordination.TaskNode
import kotlin.time.Duration.Companion.seconds

import com.ctrldevice.features.agent_engine.skills.SkillRegistry

import com.ctrldevice.features.agent_engine.intelligence.LLMBrain

/**
 * Parses raw user text into structured TaskNodes.
 * Currently uses simple regex/keyword matching.
 * In the future, this will interface with a local LLM or an external API.
 */
class CommandParser(private val brain: LLMBrain? = null) {

    // Regex Patterns moved to IntentParser to avoid duplication & violation of SRP

    // Priority Constants based on Strict Hierarchy (System > Media > Social)
    private val PRIORITY_SYSTEM = 20
    private val PRIORITY_MEDIA = 10
    private val PRIORITY_SOCIAL = 5

    suspend fun parse(input: String): TaskNode? {
        val command = input.trim()
        val id = "task_${System.currentTimeMillis()}"

        // 1. Check Skill Library (Macros) - Remains here as it maps to Tasks, not just Intents
        val macro = SkillRegistry.findMacro(command)
        if (macro != null) {
            return if (macro.template != null) {
                macro.template.invoke(id)
            } else if (macro.commands.isNotEmpty()) {
                // Generate a SequentialGroup from list of commands
                val tasks = mutableListOf<TaskNode>()
                macro.commands.forEachIndexed { index, cmd ->
                    // Recursive parse must be awaited
                    val subTask = parse(cmd) ?: TaskNode.AtomicTask(
                        id = "${id}_${index}",
                        priority = PRIORITY_SYSTEM,
                        description = cmd,
                        assignedTo = AgentType.SYSTEM,
                        estimatedDuration = 5.seconds
                    )
                    tasks.add(subTask)
                }

                TaskNode.SequentialGroup(
                    id = id,
                    priority = PRIORITY_SYSTEM,
                    description = macro.description.ifEmpty { macro.name },
                    tasks = tasks
                )
            } else {
                null
            }
        }

        // 2. Parse Intent (Delegated completely to IntentParser)
        val intent = IntentParser.parse(command)

        return when (intent) {
            is CommandIntent.GlobalAction -> {
                val description = when (intent.action) {
                    CommandIntent.GlobalActionType.HOME -> "Go to Home Screen"
                    CommandIntent.GlobalActionType.BACK -> "Go Back"
                    CommandIntent.GlobalActionType.SETTINGS -> "Open Settings"
                    CommandIntent.GlobalActionType.BATTERY -> "Check Battery Status"
                    CommandIntent.GlobalActionType.SCREEN_READ -> "Read Screen Content"
                    else -> command // Fallback
                }
                TaskNode.AtomicTask(
                    id = id,
                    priority = PRIORITY_SYSTEM,
                    description = description,
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds,
                    intent = intent
                )
            }
            is CommandIntent.Macro -> {
                if (intent.name == "system_check") {
                    // Hardcoded system check routine
                    val t1 = TaskNode.AtomicTask("${id}_1", PRIORITY_SYSTEM, "Go to Home Screen", AgentType.SYSTEM, 5.seconds)
                    val t2 = TaskNode.AtomicTask("${id}_2", PRIORITY_SYSTEM, "Open Settings", AgentType.SYSTEM, 5.seconds)
                    val t3 = TaskNode.AtomicTask("${id}_3", PRIORITY_SYSTEM, "Check Battery Status", AgentType.SYSTEM, 5.seconds)
                    TaskNode.SequentialGroup(id, PRIORITY_SYSTEM, "System Check Routine", listOf(t1, t2, t3))
                } else {
                    null // Should have been caught by SkillRegistry check if it was a user macro
                }
            }
            is CommandIntent.Click -> atomicTask(id, "Click '${intent.target}'", PRIORITY_SYSTEM)
            is CommandIntent.Input -> atomicTask(id, "Type '${intent.text}'" + (if (intent.field != null) " into '${intent.field}'" else ""), PRIORITY_SYSTEM)
            is CommandIntent.Scroll -> atomicTask(id, "Scroll ${intent.direction}", PRIORITY_SYSTEM)
            is CommandIntent.OpenApp -> atomicTask(id, "Launch ${intent.appName}", PRIORITY_SYSTEM)
            is CommandIntent.Gesture -> atomicTask(id, "${intent.type.replaceFirstChar { it.uppercase() }} ${intent.params}", PRIORITY_SYSTEM)
            is CommandIntent.FindElement -> atomicTask(id, "Find '${intent.query}'", PRIORITY_SYSTEM)
            is CommandIntent.WaitFor -> atomicTask(id, "Wait for '${intent.query}'", PRIORITY_SYSTEM, duration = 10.seconds)
            is CommandIntent.Search -> atomicTask(id, "Search for '${intent.query}'", PRIORITY_MEDIA, AgentType.RESEARCH, 10.seconds)

            is CommandIntent.Schedule -> {
                 // Optimization: Handle recursively scheduled tasks
                val subTask = parse(command.substringAfter("schedule ").substringAfter(" in ")) ?: return null

                // We need to inject the delay into the subTask.
                // Since TaskNode is immutable (val), we copy it if it's an AtomicTask.
                if (subTask is TaskNode.AtomicTask) {
                    subTask.copy(scheduledStart = System.currentTimeMillis() + intent.delayMs)
                } else {
                    // For groups, we might need a wrapper or apply delay to the first task?
                    // For now, simplify to just returning the subTask (delay logic for groups is complex)
                    // Or wrap in a delayed AtomicTask that expands later?
                    // Let's stick to the previous simple AtomicTask representation for the schedule itself.
                    val subDescription = when(val sub = intent.intent) {
                        is CommandIntent.Click -> "Click ${sub.target}"
                        is CommandIntent.GlobalAction -> sub.action.name
                        else -> "Scheduled Action"
                    }
                    TaskNode.AtomicTask(
                        id = id,
                        priority = PRIORITY_SYSTEM,
                        description = "Scheduled: $subDescription",
                        assignedTo = AgentType.SYSTEM,
                        estimatedDuration = 5.seconds,
                        scheduledStart = System.currentTimeMillis() + intent.delayMs
                    )
                }
            }

            is CommandIntent.Unknown -> {
                 // 3. LLM Fallback (Long Horizon / Complex Tasks)
                if (brain != null) {
                    // Optimization: Now suspending, no runBlocking needed
                    brain.planTask(command)
                } else {
                    null
                }
            }
            is CommandIntent.Empty -> null
        }
    }

    private fun atomicTask(
        id: String,
        description: String,
        priority: Int,
        agent: AgentType = AgentType.SYSTEM,
        duration: kotlin.time.Duration = 5.seconds,
        intent: CommandIntent? = null
    ): TaskNode.AtomicTask {
        return TaskNode.AtomicTask(id, priority, description, agent, duration, intent = intent)
    }
}
