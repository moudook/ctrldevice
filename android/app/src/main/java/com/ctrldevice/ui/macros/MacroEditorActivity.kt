package com.ctrldevice.ui.macros

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.ctrldevice.R
import com.ctrldevice.features.agent_engine.coordination.Message
import com.ctrldevice.features.agent_engine.coordination.MessageBus
import com.ctrldevice.features.agent_engine.skills.Macro
import com.ctrldevice.features.agent_engine.skills.SkillRegistry
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class MacroEditorActivity : Activity() {

    private lateinit var nameInput: TextInputEditText
    private lateinit var descInput: TextInputEditText
    private lateinit var triggersInput: TextInputEditText
    private lateinit var commandsInput: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var deleteButton: Button
    private lateinit var recordButton: Button

    private var editingMacroName: String? = null
    private val recorder = com.ctrldevice.features.agent_engine.skills.MacroRecorder()
    private var isRecording = false
    private val messageBus = MessageBus.Instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro_editor)

        nameInput = findViewById(R.id.editName)
        descInput = findViewById(R.id.editDescription)
        triggersInput = findViewById(R.id.editTriggers)
        commandsInput = findViewById(R.id.editCommands)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        deleteButton = findViewById(R.id.deleteButton)
        recordButton = findViewById(R.id.recordButton)

        editingMacroName = intent.getStringExtra("MACRO_NAME")

        if (editingMacroName != null) {
            loadMacro(editingMacroName!!)
        } else {
            deleteButton.isEnabled = false // Cannot delete a new macro
        }

        saveButton.setOnClickListener { saveMacro() }
        cancelButton.setOnClickListener { finish() }
        deleteButton.setOnClickListener { deleteMacro() }
        recordButton.setOnClickListener { toggleRecording() }

        // Auto-start recording if requested (e.g. from "Watch Me" intervention)
        if (intent.getBooleanExtra("AUTO_RECORD", false)) {
            // Give the UI a moment to settle, then start
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                toggleRecording()
            }, 500)
        }
    }

    private fun toggleRecording() {
        if (!isRecording) {
            // Start Recording
            if (com.ctrldevice.service.accessibility.ControllerService.instance == null) {
                Toast.makeText(this, "Accessibility Service not connected!", Toast.LENGTH_SHORT).show()
                return
            }

            recorder.startRecording()
            isRecording = true
            recordButton.text = "⏹ Stop Recording"
            recordButton.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)

            // Notify Overlay
            CoroutineScope(Dispatchers.IO).launch {
                messageBus.send(Message.DataAvailable(
                    from = "MacroEditor",
                    to = "All",
                    timestamp = Clock.System.now(),
                    key = "status",
                    data = "Recording Actions..."
                ))
            }

            Toast.makeText(this, "Recording... Perform actions now.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true) // Minimize to let user act
        } else {
            // Stop Recording
            val cmds = recorder.stopRecording()
            isRecording = false
            recordButton.text = "🔴 Record Actions (Watch & Learn)"
            recordButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFD32F2F.toInt())

            // Reset Overlay
            CoroutineScope(Dispatchers.IO).launch {
                messageBus.send(Message.DataAvailable(
                    from = "MacroEditor",
                    to = "All",
                    timestamp = Clock.System.now(),
                    key = "status",
                    data = "Idle"
                ))
            }

            if (cmds.isNotEmpty()) {
                val currentText = commandsInput.text.toString()
                val newText = if (currentText.isBlank()) {
                    cmds.joinToString("\n")
                } else {
                    currentText + "\n" + cmds.joinToString("\n")
                }
                commandsInput.setText(newText)
                Toast.makeText(this, "Captured ${cmds.size} actions.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No actions captured.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMacro(name: String) {
        val macro = SkillRegistry.getAllMacros().find { it.name == name } ?: return

        nameInput.setText(macro.name)
        descInput.setText(macro.description)
        triggersInput.setText(macro.triggers.joinToString(", "))
        commandsInput.setText(macro.commands.joinToString("\n"))

        // Disable name editing for now to keep ID simple, or handle rename logic
        // nameInput.isEnabled = false
    }

    private fun saveMacro() {
        val name = nameInput.text.toString().trim()
        val desc = descInput.text.toString().trim()
        val triggers = triggersInput.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val commands = commandsInput.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        if (name.isEmpty()) {
            nameInput.error = "Name required"
            return
        }
        if (triggers.isEmpty()) {
            triggersInput.error = "At least one trigger required"
            return
        }
        if (commands.isEmpty()) {
            commandsInput.error = "At least one command required"
            return
        }

        val macro = Macro(
            name = name,
            description = desc,
            triggers = triggers,
            commands = commands
        )

        SkillRegistry.addMacro(this, macro)
        Toast.makeText(this, "Skill Saved", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun deleteMacro() {
        if (editingMacroName != null) {
            val macro = SkillRegistry.getAllMacros().find { it.name == editingMacroName }
            if (macro != null) {
                SkillRegistry.removeMacro(this, macro)
                Toast.makeText(this, "Skill Deleted", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
