package com.ctrldevice.features.agent_engine.skills

import com.ctrldevice.features.agent_engine.coordination.TaskNode

/**
 * Represents a reusable sequence of actions (Macro).
 */
data class Macro(
    val name: String,
    val description: String,
    val triggers: List<String>, // Keywords that trigger this macro
    val template: (String) -> TaskNode // Function to generate the TaskNode graph
)
