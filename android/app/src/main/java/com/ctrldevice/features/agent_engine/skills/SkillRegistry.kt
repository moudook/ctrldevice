package com.ctrldevice.features.agent_engine.skills

import com.ctrldevice.features.agent_engine.coordination.AgentType
import com.ctrldevice.features.agent_engine.coordination.TaskNode
import kotlin.time.Duration.Companion.seconds

/**
 * Registry for high-level skills (macros).
 * Allows defining complex workflows dynamically.
 */
object SkillRegistry {
    private val macros = mutableListOf<Macro>()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        // Example Macro: Daily Briefing
        register(
            Macro(
                name = "Daily Briefing",
                description = "Checks weather and news",
                triggers = listOf("daily briefing", "morning routine"),
                template = { id ->
                    val t1 = TaskNode.AtomicTask(
                        id = "${id}_1",
                        priority = 10,
                        description = "Open Chrome for 'Weather today'",
                        assignedTo = AgentType.RESEARCH,
                        estimatedDuration = 10.seconds
                    )
                    val t2 = TaskNode.AtomicTask(
                        id = "${id}_2",
                        priority = 10,
                        description = "Open Chrome for 'Tech News'",
                        assignedTo = AgentType.RESEARCH,
                        estimatedDuration = 10.seconds
                    )

                    TaskNode.SequentialGroup(
                        id = id,
                        priority = 10,
                        description = "Daily Briefing Routine",
                        tasks = listOf(t1, t2)
                    )
                }
            )
        )

        // Example Macro: Cleanup
        register(
            Macro(
                name = "Cleanup",
                description = "Closes background apps (simulated by going home and clearing recents)",
                triggers = listOf("cleanup", "close all"),
                template = { id ->
                    val t1 = TaskNode.AtomicTask(
                        id = "${id}_1",
                        priority = 10,
                        description = "Go to Home Screen",
                        assignedTo = AgentType.SYSTEM,
                        estimatedDuration = 5.seconds
                    )
                    // In a real implementation, we'd add "Open Recents" and "Click Clear All"

                    TaskNode.SequentialGroup(
                        id = id,
                        priority = 10,
                        description = "Cleanup Routine",
                        tasks = listOf(t1)
                    )
                }
            )
        )
    }

    fun register(macro: Macro) {
        macros.add(macro)
    }

    fun findMacro(input: String): Macro? {
        return macros.find { macro ->
            macro.triggers.any { trigger -> input.contains(trigger, ignoreCase = true) }
        }
    }
}
