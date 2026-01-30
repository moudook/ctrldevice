package com.ctrldevice.features.agent_engine.intelligence

import com.ctrldevice.features.agent_engine.coordination.AgentType
import com.ctrldevice.features.agent_engine.coordination.TaskNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RuleBasedBrainTest {

    private val brain = RuleBasedBrain()

    private fun createTask(description: String): TaskNode.AtomicTask {
        return TaskNode.AtomicTask(
            id = "test_task",
            priority = 10,
            description = description,
            assignedTo = AgentType.SYSTEM,
            estimatedDuration = 5.seconds
        )
    }

    @Test
    fun `propose click when element is visible`() = runBlocking {
        val task = createTask("click Submit")
        val screenContext = "- Submit (Clickable)\n- Cancel (Clickable)"

        val thought = brain.proposeNextStep(task, screenContext, emptyList(), null)

        assertEquals("click_element", thought.toolName)
        assertEquals("Submit", thought.toolParams)
    }

    @Test
    fun `propose scroll when element is missing`() = runBlocking {
        val task = createTask("click Submit")
        val screenContext = "- Header Text\n- Item 1\n- Item 2"

        val thought = brain.proposeNextStep(task, screenContext, emptyList(), null)

        assertEquals("scroll", thought.toolName)
        assertEquals("down", thought.toolParams)
        // Verify reasoning logic
        assert(thought.reasoning.contains("not visible"))
    }

    @Test
    fun `propose read_screen when context is empty`() = runBlocking {
        val task = createTask("click Submit")
        val screenContext = "" // Empty context

        val thought = brain.proposeNextStep(task, screenContext, emptyList(), null)

        assertEquals("read_screen", thought.toolName)
    }

    @Test
    fun `propose type text`() = runBlocking {
        val task = createTask("type Hello into Username")
        val screenContext = "- Username (Editable)"

        val thought = brain.proposeNextStep(task, screenContext, emptyList(), null)

        assertEquals("input_text", thought.toolName)
        assertEquals("Hello into Username", thought.toolParams)
    }

    @Test
    fun `propose global action`() = runBlocking {
        val task = createTask("go home")
        val screenContext = "Any context"

        val thought = brain.proposeNextStep(task, screenContext, emptyList(), null)

        assertEquals("go_home", thought.toolName)
    }
}
