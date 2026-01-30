package com.ctrldevice.features.agent_engine.safety

import com.ctrldevice.features.agent_engine.coordination.AgentAction
import com.ctrldevice.features.agent_engine.coordination.StateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AgentGovernorTest {

    // specific mock not strictly needed for detectLoop if we access it directly,
    // but Governor constructor needs it.
    private val mockStateManager = mock(StateManager::class.java)
    private val governor = AgentGovernor(mockStateManager)

    private fun action(desc: String) = AgentAction(description = desc)

    @Test
    fun `detectLoop returns false for empty history`() {
        val history = emptyList<AgentAction>()
        assertFalse(governor.detectLoop(history))
    }

    @Test
    fun `detectLoop returns false for non-looping sequence`() {
        val history = listOf(
            action("click A"),
            action("click B"),
            action("click C")
        )
        assertFalse(governor.detectLoop(history))
    }

    @Test
    fun `detects simple repeat loop L=1 (5 repeats)`() {
        // A, A, A, A, A -> Loop
        val history = listOf(
            action("scroll down"),
            action("scroll down"),
            action("scroll down"),
            action("scroll down"),
            action("scroll down")
        )
        assertTrue("Should detect loop on 5th repeat", governor.detectLoop(history))
    }

    @Test
    fun `ignores simple repeat loop less than 5 times`() {
        // A, A, A, A -> No Loop yet (allows for some scrolling)
        val history = listOf(
            action("scroll down"),
            action("scroll down"),
            action("scroll down"),
            action("scroll down")
        )
        assertFalse("Should allow 4 repeats", governor.detectLoop(history))
    }

    @Test
    fun `detects complex loop L=2 (3 repeats)`() {
        // A, B, A, B, A, B -> Loop
        val history = listOf(
            action("click A"),
            action("back"),
            action("click A"),
            action("back"),
            action("click A"),
            action("back")
        )
        assertTrue("Should detect ABABAB pattern", governor.detectLoop(history))
    }

    @Test
    fun `detects complex loop L=3 (3 repeats)`() {
        // A, B, C, A, B, C, A, B, C -> Loop
        val history = listOf(
            action("A"), action("B"), action("C"),
            action("A"), action("B"), action("C"),
            action("A"), action("B"), action("C")
        )
        assertTrue("Should detect ABCABCABC pattern", governor.detectLoop(history))
    }

    @Test
    fun `ignores complex pattern with insufficient repeats`() {
        // A, B, A, B -> Only 2 repeats (threshold is 3 for L>1)
        val history = listOf(
            action("click A"),
            action("back"),
            action("click A"),
            action("back")
        )
        assertFalse("Should allow 2 repeats of complex pattern", governor.detectLoop(history))
    }

    @Test
    fun `detects loop even with prefix history`() {
        // ...random..., A, A, A, A, A
        val history = listOf(
            action("start"),
            action("login"),
            action("scroll"),
            action("scroll"),
            action("scroll"),
            action("scroll"),
            action("scroll")
        )
        assertTrue(governor.detectLoop(history))
    }
}
