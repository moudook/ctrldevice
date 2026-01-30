package com.ctrldevice.features.agent_engine.skills

import android.content.Context
import android.util.Log
import com.ctrldevice.features.agent_engine.coordination.AgentType
import com.ctrldevice.features.agent_engine.coordination.TaskNode
import com.google.gson.Gson
import kotlin.time.Duration.Companion.seconds

import java.io.File

/**
 * Registry for high-level skills (macros).
 * Allows defining complex workflows dynamically.
 */
object SkillRegistry {
    val macros = mutableListOf<Macro>()
    // Optimization: Flat index for O(1) lookup by trigger
    private val triggerIndex = mutableMapOf<String, Macro>()

    private var isInitialized = false
    private const val USER_SKILLS_FILE = "user_skills.json"

    fun initialize(context: Context) {
        if (isInitialized) return

        val userFile = File(context.filesDir, USER_SKILLS_FILE)
        if (userFile.exists()) {
            loadFromStorage(userFile)
        } else {
            loadFromAssets(context)
            registerDefaults()
        }
        rebuildIndex()
        isInitialized = true
    }

    private fun rebuildIndex() {
        triggerIndex.clear()
        macros.forEach { macro ->
            macro.triggers.forEach { trigger ->
                triggerIndex[trigger.lowercase()] = macro
            }
        }
    }

    private fun loadFromStorage(file: File) {
        try {
            val jsonString = file.readText()
            val gson = Gson()
            val skillData = gson.fromJson(jsonString, SkillData::class.java)
            macros.clear()
            skillData.macros.forEach { macros.add(it) }
            Log.d("SkillRegistry", "Loaded ${macros.size} macros from storage")
        } catch (e: Exception) {
            Log.e("SkillRegistry", "Error loading from storage", e)
            // Fallback if file is corrupted
            macros.clear()
        }
    }

    private fun loadFromAssets(context: Context) {
        try {
            val jsonString = context.assets.open("skills.json").bufferedReader().use { it.readText() }
            val gson = Gson()
            val skillData = gson.fromJson(jsonString, SkillData::class.java)
            skillData.macros.forEach { register(it) }
            Log.d("SkillRegistry", "Loaded ${skillData.macros.size} macros from assets")
        } catch (e: Exception) {
            Log.e("SkillRegistry", "Error loading skills.json", e)
        }
    }

    fun save(context: Context) {
        try {
            val gson = Gson()
            val data = SkillData(macros)
            val jsonString = gson.toJson(data)
            val file = File(context.filesDir, USER_SKILLS_FILE)
            file.writeText(jsonString)
            Log.d("SkillRegistry", "Saved ${macros.size} macros to storage")
        } catch (e: Exception) {
            Log.e("SkillRegistry", "Error saving to storage", e)
        }
    }

    fun addMacro(context: Context, macro: Macro) {
        // Remove existing if name matches (update)
        macros.removeAll { it.name == macro.name }
        macros.add(macro)
        rebuildIndex()
        save(context)
    }

    fun removeMacro(context: Context, macro: Macro) {
        macros.remove(macro)
        rebuildIndex()
        save(context)
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
        // No rebuildIndex here as this is internal/batch, handled by initialize/addMacro
    }

    fun findMacro(input: String): Macro? {
        // Optimization: Fast lookup for exact matches or contains
        // 1. Exact trigger match
        val lowerInput = input.trim().lowercase()
        val exactMatch = triggerIndex[lowerInput]
        if (exactMatch != null) return exactMatch

        // 2. Fallback to scanning if input contains trigger (e.g. "run daily briefing now")
        // Since the index keys are triggers, we can iterate them.
        // It's still O(T) where T is total triggers, but avoids iterating Macro objects
        val foundTrigger = triggerIndex.keys.find { trigger -> lowerInput.contains(trigger) }
        return if (foundTrigger != null) triggerIndex[foundTrigger] else null
    }

    fun getAllMacros(): List<Macro> {
        return macros.toList()
    }
}

data class SkillData(
    val macros: List<Macro>
)
