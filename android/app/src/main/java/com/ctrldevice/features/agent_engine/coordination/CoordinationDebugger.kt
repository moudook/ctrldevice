package com.ctrldevice.features.agent_engine.coordination

import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

class CoordinationDebugger(
    private val resourceManager: ResourceManager,
    private val taskGraph: TaskGraph,
    private val messageBus: MessageBus
) {
    fun generateReport(): String {
        return buildString {
            appendLine("=== CtrlDevice Coordination Report ===")
            appendLine()
            
            appendLine("📊 Task Graph Status:")
            appendLine(taskGraph.toMermaidDiagram())
            appendLine()
            
            appendLine("🔒 Resource Status:")
            resourceManager.getCurrentLeases().forEach { lease ->
                appendLine("  - ${lease.resource}: held by ${lease.owner}")
            }
            appendLine()
            
            appendLine("📬 Recent Messages:")
            messageBus.getHistory().takeLast(10).forEach { msg ->
                appendLine("  [${msg.timestamp}] ${msg.from} -> ${msg.to}: ${msg::class.simpleName}")
            }
        }
    }

    fun startLiveMonitor() {
        CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                println(generateReport())
                delay(2.seconds)
            }
        }
    }
}
