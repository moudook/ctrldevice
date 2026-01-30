package com.ctrldevice.features.agent_engine.coordination

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStreamWriter
import java.util.Collections
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

// Placeholder for Agent Action definition
data class AgentAction(
    val description: String,
    val screenshotId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
data class ScreenSnapshot(val timestamp: Long)

data class AgentState(
    val agentId: AgentId,
    val currentTask: String,
    val progress: Float,
    val dataCollected: Map<String, Any>,
    val resourcesHeld: List<Resource>,
    val lastScreenState: ScreenSnapshot?,
    val actionHistory: List<AgentAction>
)

class StateManager(private val context: Context) {
    private val checkpoints = ConcurrentHashMap<AgentId, AgentState>()
    // Optimization: LinkedList for O(1) removals at head (rolling window)
    private val actionLogs = ConcurrentHashMap<AgentId, MutableList<AgentAction>>()
    private val gson = Gson()
    private val fileName = "agent_memory.json"
    private val screenshotDirName = "screenshots"

    // Optimization: Background scope for I/O operations with a bounded dispatcher
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val MAX_HISTORY_SIZE = 100
    private val saveChannel = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    init {
        loadFromDisk()
        // Ensure screenshot directory exists
        val dir = File(context.filesDir, screenshotDirName)
        if (!dir.exists()) dir.mkdirs()

        // Debounced Save Loop
        scope.launch {
            for (signal in saveChannel) {
                // Wait for a short buffer time to accumulate rapid changes
                kotlinx.coroutines.delay(500)
                saveToDisk()
            }
        }
    }

    fun logAction(agentId: AgentId, description: String, screenshotId: String? = null) {
        val history = actionLogs.computeIfAbsent(agentId) {
            Collections.synchronizedList(LinkedList())
        }
        val action = AgentAction(description, screenshotId = screenshotId)

        // Optimization: Rolling window to prevent OOM
        // Synchronizing on the list ensures atomic check-then-act
        synchronized(history) {
            if (history.size >= MAX_HISTORY_SIZE) {
                // LinkedList.removeAt(0) is O(1)
                history.removeAt(0)
            }
            history.add(action)
        }

        // Trigger save (debounced)
        saveChannel.trySend(Unit)
    }

    fun saveScreenshot(base64Image: String): String? {
        return try {
            val timestamp = System.currentTimeMillis()
            val filename = "shot_$timestamp.jpg"
            val dir = File(context.filesDir, screenshotDirName)
            val file = File(dir, filename)

            val bytes = android.util.Base64.decode(base64Image, android.util.Base64.NO_WRAP)
            file.writeBytes(bytes)

            filename // Return ID (filename)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getActionHistory(agentId: AgentId): List<AgentAction> {
        // Return a copy to avoid ConcurrentModificationException during iteration by caller
        val list = actionLogs[agentId] ?: return emptyList()
        synchronized(list) {
            return ArrayList(list)
        }
    }

    private fun saveToDisk() {
        try {
            // Optimization: Streaming write to avoid loading full JSON string into memory
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    gson.toJson(actionLogs, writer)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromDisk() {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                // Optimization: Streaming read
                file.inputStream().reader().use { reader ->
                    // We deserialize into LinkedLists to maintain our chosen structure
                    val type = object : TypeToken<ConcurrentHashMap<AgentId, LinkedList<AgentAction>>>() {}.type
                    val loaded: ConcurrentHashMap<AgentId, LinkedList<AgentAction>>? = gson.fromJson(reader, type)

                    if (loaded != null) {
                        // Ensure loaded lists are wrapped in synchronized lists to maintain contract
                        loaded.forEach { (id, list) ->
                            actionLogs[id] = Collections.synchronizedList(list)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun checkpoint(agentId: AgentId) {
        // In a real implementation, this would call agent.captureState()
        // For now, we stub it to allow compilation
        // saveToDatabase(state)
    }

    // Explicit checkpoint when we have the state object
    fun saveState(state: AgentState) {
        checkpoints[state.agentId] = state
        // saveToDatabase(state)
    }

    fun restore(agentId: AgentId): AgentState? {
        return checkpoints[agentId] // ?: loadFromDatabase(agentId)
    }
}
