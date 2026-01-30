package com.ctrldevice.features.agent_engine.coordination

/**
 * Defines the resource requirements for different types of agents/tasks.
 * Decouples resource policy from execution logic.
 */
class ResourceStrategy {
    fun determineRequiredResources(task: TaskNode.AtomicTask): List<Resource> {
        return when (task.assignedTo) {
            AgentType.RESEARCH -> listOf(
                Resource.App("com.android.chrome"),
                Resource.Screen(isExclusive = true),
                Resource.Network(priority = task.priority)
            )
            AgentType.SOCIAL -> listOf(
                Resource.App("com.whatsapp"),
                Resource.Screen(isExclusive = true)
            )
            AgentType.MEDIA -> listOf(
                Resource.App("com.google.android.youtube"),
                Resource.Screen(isExclusive = false)
            )
            AgentType.SYSTEM -> listOf(
                Resource.Storage("/sdcard/Download"),
                Resource.Screen(isExclusive = true) // Essential for priority preemption (System > Media > Social)
            )
        }
    }
}
