package com.ctrldevice.features.agent_engine.parsing

import com.ctrldevice.features.agent_engine.coordination.AgentType
import com.ctrldevice.features.agent_engine.coordination.TaskNode
import kotlin.time.Duration.Companion.seconds

import com.ctrldevice.features.agent_engine.skills.SkillRegistry

/**
 * Parses raw user text into structured TaskNodes.
 * Currently uses simple regex/keyword matching.
 * In the future, this will interface with a local LLM or an external API.
 */
class CommandParser {

    // Regex Patterns for stricter command matching
    private val CLICK_REGEX = Regex("""(?i)^click\s+(?:on\s+)?(.+)$""")
    private val TYPE_REGEX = Regex("""(?i)^type\s+(.+?)(?:\s+into\s+(.+))?$""")
    private val SCROLL_REGEX = Regex("""(?i)^scroll\s+(.+)$""")
    private val SEARCH_REGEX = Regex("""(?i)^(?:search(?:\s+for)?|browse)\s+(.+)$""")
    private val OPEN_REGEX = Regex("""(?i)^(?:open|launch)\s+(.+)$""")

    fun parse(input: String): TaskNode? {
        val command = input.trim()
        val id = "task_${System.currentTimeMillis()}"

        // 1. Check Skill Library (Macros)
        val macro = SkillRegistry.findMacro(command)
        if (macro != null) {
            return macro.template(id)
        }

        // 2. Fallback to Regex/Keyword matching
        return when {
            command.contains("system check", ignoreCase = true) -> {
                val t1 = TaskNode.AtomicTask(
                    id = "${id}_1",
                    priority = 10,
                    description = "Go to Home Screen",
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
                val t2 = TaskNode.AtomicTask(
                    id = "${id}_2",
                    priority = 10,
                    description = "Open Settings",
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
                val t3 = TaskNode.AtomicTask(
                    id = "${id}_3",
                    priority = 10,
                    description = "Check Battery Status",
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )

                TaskNode.SequentialGroup(
                    id = id,
                    priority = 10,
                    description = "System Check Routine",
                    tasks = listOf(t1, t2, t3)
                )
            }
            command.equals("home", ignoreCase = true) || command.contains("go home", ignoreCase = true) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = "Go to Home Screen",
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
            }
            command.equals("settings", ignoreCase = true) || command.contains("open settings", ignoreCase = true) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = "Open Settings",
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
            }
            command.contains("battery", ignoreCase = true) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = "Check Battery Status",
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
            }
            command.equals("back", ignoreCase = true) || command.contains("go back", ignoreCase = true) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = "Go Back",
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
            }
            command.contains("screen", ignoreCase = true) || command.contains("read", ignoreCase = true) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = "Read Screen Content",
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
            }
            CLICK_REGEX.matches(command) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = command,
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
            }
            TYPE_REGEX.matches(command) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = command,
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
            }
            SCROLL_REGEX.matches(command) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = command,
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
            }
            SEARCH_REGEX.matches(command) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = command,
                    assignedTo = AgentType.RESEARCH,
                    estimatedDuration = 10.seconds
                )
            }
            OPEN_REGEX.matches(command) -> {
                TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = command,
                    assignedTo = AgentType.SYSTEM,
                    estimatedDuration = 5.seconds
                )
            }
            else -> null
        }
    }
}
