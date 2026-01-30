package com.ctrldevice.features.agent_engine.coordination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class TaskGraphTest {

    private fun createAtomicTask(id: String): TaskNode.AtomicTask {
        return TaskNode.AtomicTask(
            id = id,
            priority = 1,
            description = "Test Task $id",
            assignedTo = AgentType.SYSTEM,
            estimatedDuration = 1.seconds
        )
    }

    @Test
    fun `independent nodes are ready immediately`() {
        val graph = TaskGraph()
        val t1 = createAtomicTask("t1")
        val t2 = createAtomicTask("t2")

        graph.addNode(t1)
        graph.addNode(t2)

        val ready = graph.getReadyTasks()
        assertEquals(2, ready.size)
        assertTrue(ready.any { it.id == "t1" })
        assertTrue(ready.any { it.id == "t2" })
    }

    @Test
    fun `dependent nodes wait for dependencies`() {
        val graph = TaskGraph()
        val t1 = createAtomicTask("t1")
        val t2 = createAtomicTask("t2")

        graph.addNode(t1)
        graph.addNode(t2)
        graph.addEdge(from = "t1", to = "t2")

        // Initially only t1 should be ready
        var ready = graph.getReadyTasks()
        assertEquals(1, ready.size)
        assertEquals("t1", ready.first().id)

        // Complete t1
        graph.markCompleted("t1", TaskResult.success())

        // Now t2 should be ready
        ready = graph.getReadyTasks()
        assertEquals(1, ready.size)
        assertEquals("t2", ready.first().id)
    }

    @Test
    fun `diamond dependency graph`() {
        // A -> B, A -> C, (B, C) -> D
        val graph = TaskGraph()
        val a = createAtomicTask("A")
        val b = createAtomicTask("B")
        val c = createAtomicTask("C")
        val d = createAtomicTask("D")

        graph.addNode(a)
        graph.addNode(b)
        graph.addNode(c)
        graph.addNode(d)

        graph.addEdge("A", "B")
        graph.addEdge("A", "C")
        graph.addEdge("B", "D")
        graph.addEdge("C", "D")

        // 1. Only A ready
        assertEquals(listOf("A"), graph.getReadyTasks().map { it.id })

        // 2. Complete A -> B and C ready
        graph.markCompleted("A", TaskResult.success())
        val readyAfterA = graph.getReadyTasks().map { it.id }.sorted()
        assertEquals(listOf("B", "C"), readyAfterA)

        // 3. Complete B -> D still waits for C
        graph.markCompleted("B", TaskResult.success())
        assertEquals(listOf("C"), graph.getReadyTasks().map { it.id })

        // 4. Complete C -> D ready
        graph.markCompleted("C", TaskResult.success())
        assertEquals(listOf("D"), graph.getReadyTasks().map { it.id })
    }

    @Test
    fun `adding edge updates readiness dynamically`() {
        val graph = TaskGraph()
        val t1 = createAtomicTask("t1")
        val t2 = createAtomicTask("t2")

        // Add both, both ready
        graph.addNode(t1)
        graph.addNode(t2)
        assertEquals(2, graph.getReadyTasks().size)

        // Add edge t1 -> t2, t2 should no longer be ready
        graph.addEdge("t1", "t2")
        val ready = graph.getReadyTasks()
        assertEquals(1, ready.size)
        assertEquals("t1", ready.first().id)
    }
}
