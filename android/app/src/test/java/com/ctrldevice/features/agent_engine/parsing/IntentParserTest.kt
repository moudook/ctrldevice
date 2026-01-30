package com.ctrldevice.features.agent_engine.parsing

import org.junit.Test
import org.junit.Assert.*

class IntentParserTest {

    @Test
    fun `parse click command`() {
        val input = "click on Submit Button"
        val result = IntentParser.parse(input)

        assertTrue(result is CommandIntent.Click)
        assertEquals("Submit Button", (result as CommandIntent.Click).target)
    }

    @Test
    fun `parse type command with target`() {
        val input = "type Hello World into Message Input"
        val result = IntentParser.parse(input)

        assertTrue(result is CommandIntent.Input)
        val inputIntent = result as CommandIntent.Input
        assertEquals("Hello World", inputIntent.text)
        assertEquals("Message Input", inputIntent.field)
    }

    @Test
    fun `parse type command without target`() {
        val input = "type password123"
        val result = IntentParser.parse(input)

        assertTrue(result is CommandIntent.Input)
        val inputIntent = result as CommandIntent.Input
        assertEquals("password123", inputIntent.text)
        assertNull(inputIntent.field)
    }

    @Test
    fun `parse scroll command`() {
        val input = "scroll down"
        val result = IntentParser.parse(input)

        assertTrue(result is CommandIntent.Scroll)
        assertEquals("down", (result as CommandIntent.Scroll).direction)
    }

    @Test
    fun `parse global action home`() {
        val input = "go home"
        val result = IntentParser.parse(input)

        assertTrue(result is CommandIntent.GlobalAction)
        assertEquals(CommandIntent.GlobalActionType.HOME, (result as CommandIntent.GlobalAction).action)
    }

    @Test
    fun `parse scheduled command`() {
        val input = "schedule click Confirm in 5 minutes"
        val result = IntentParser.parse(input)

        assertTrue(result is CommandIntent.Schedule)
        val scheduleIntent = result as CommandIntent.Schedule
        assertEquals(5 * 60 * 1000L, scheduleIntent.delayMs)

        assertTrue(scheduleIntent.intent is CommandIntent.Click)
        assertEquals("Confirm", (scheduleIntent.intent as CommandIntent.Click).target)
    }

    @Test
    fun `parse complex recursive schedule`() {
        // "schedule schedule go home in 10 seconds in 1 minute" -> Not explicitly supported by regex but let's see
        // The regex is ^schedule (.+) in (\d+) (units)$
        // subCommand = "schedule go home in 10 seconds"

        val input = "schedule schedule go home in 10 seconds in 1 minute"
        val result = IntentParser.parse(input)

        assertTrue(result is CommandIntent.Schedule)
        val outerSchedule = result as CommandIntent.Schedule
        assertEquals(60 * 1000L, outerSchedule.delayMs)

        assertTrue(outerSchedule.intent is CommandIntent.Schedule)
        val innerSchedule = outerSchedule.intent as CommandIntent.Schedule
        assertEquals(10 * 1000L, innerSchedule.delayMs)

        assertTrue(innerSchedule.intent is CommandIntent.GlobalAction)
    }

    @Test
    fun `parse unknown command fallback`() {
        val input = "do a barrel roll"
        val result = IntentParser.parse(input)

        assertTrue(result is CommandIntent.Unknown)
        assertEquals("do a barrel roll", (result as CommandIntent.Unknown).rawCommand)
    }
}
