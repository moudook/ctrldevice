package com.ctrldevice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.ctrldevice.features.agent_engine.coordination.*
import com.ctrldevice.service.accessibility.ControllerService
import com.ctrldevice.features.agent_engine.safety.AgentGovernor
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class MainActivity : Activity() {

    private lateinit var inputField: EditText
    private lateinit var executeButton: Button
    private lateinit var micFab: FloatingActionButton
    private lateinit var statusText: TextView
    private lateinit var settingsButton: Button
    private lateinit var stopButton: Button
    private lateinit var logText: TextView
    private lateinit var brainReasoningText: TextView
    private lateinit var strategyText: TextView

    private var speechRecognizer: SpeechRecognizer? = null

    // Agent Engine Components
    private val messageBus = MessageBus()
    private lateinit var stateManager: StateManager
    private lateinit var resourceManager: ResourceManager
    private lateinit var agentGovernor: AgentGovernor
    private val ORCHESTRATOR_ID = "Orchestrator"
    private val commandParser = com.ctrldevice.features.agent_engine.parsing.CommandParser()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize components that require Context
        stateManager = StateManager(applicationContext)
        resourceManager = ResourceManager(messageBus, stateManager)
        agentGovernor = AgentGovernor(stateManager)

        inputField = findViewById(R.id.inputField)
        executeButton = findViewById(R.id.executeButton)
        micFab = findViewById(R.id.micFab)
        statusText = findViewById(R.id.statusText)
        settingsButton = findViewById(R.id.settingsButton)
        stopButton = findViewById(R.id.stopButton)
        logText = findViewById(R.id.logText)
        brainReasoningText = findViewById(R.id.brainReasoningText)
        strategyText = findViewById(R.id.strategyText)

        // Initialize Speech Recognizer
        setupSpeechRecognizer()

        micFab.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput()
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            }
        }

        executeButton.setOnClickListener {
            val command = inputField.text.toString()
            if (command.isNotBlank()) {
                agentGovernor.reset() // Reset safety checks on new run
                executeCommand(command)
            } else {
                Toast.makeText(this, "Please enter a command", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener {
            agentGovernor.triggerEmergencyStop()
            statusText.text = "Status: EMERGENCY STOP TRIGGERED"
            Toast.makeText(this, "Agent Stopped!", Toast.LENGTH_LONG).show()
        }

        settingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        subscribeToAgentThoughts()
        subscribeToSafetyEvents()
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        } else {
            micFab.isEnabled = false
            micFab.backgroundTintList = ColorStateList.valueOf(0xFFCCCCCC.toInt())
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell the agent what to do...")
        }
        try {
            speechRecognizer?.startListening(intent)
            statusText.text = "Status: Listening..."
            setMicListeningState(true)
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting speech recognition: ${e.message}", Toast.LENGTH_SHORT).show()
            setupSpeechRecognizer() // Re-init on failure
        }
    }

    private fun setMicListeningState(isListening: Boolean) {
        if (isListening) {
            micFab.backgroundTintList = ColorStateList.valueOf(0xFFFF4081.toInt()) // Pinkish/Red for listening
            micFab.setImageResource(android.R.drawable.ic_btn_speak_now)
        } else {
            micFab.backgroundTintList = ColorStateList.valueOf(0xFF2196F3.toInt()) // Original blue
            micFab.setImageResource(android.R.drawable.ic_btn_speak_now)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                setMicListeningState(false)
                statusText.text = "Status: Processing Speech..."
            }

            override fun onError(error: Int) {
                setMicListeningState(false)
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Speech Error: $error"
                }
                statusText.text = "Status: $message"

                // For certain errors, it's better to recreate the recognizer
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                    setupSpeechRecognizer()
                }
            }

            override fun onResults(results: Bundle?) {
                setMicListeningState(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0]
                    inputField.setText(command)
                    executeCommand(command)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    private fun subscribeToSafetyEvents() {
        CoroutineScope(Dispatchers.Main).launch {
            ControllerService.userInterrupts.collect { interrupted ->
                if (interrupted) {
                    agentGovernor.triggerEmergencyStop()
                    statusText.text = "Status: PAUSED by User Touch"
                    appendLog("[SAFETY] User touch detected. Emergency Stop Triggered.")
                    Toast.makeText(this@MainActivity, "Agent Paused by Touch", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun subscribeToAgentThoughts() {
        val channel = messageBus.register(ORCHESTRATOR_ID)
        CoroutineScope(Dispatchers.Main).launch {
            for (msg in channel) {
                val timestamp = msg.timestamp.toString().substringAfter("T").substringBefore(".")
                val logEntry = when (msg) {
                    is Message.DataAvailable -> {
                        val dataString = msg.data.toString()
                        val text = "${msg.from}: $dataString"

                        // DASHBOARD UPDATES
                        if (dataString.contains("Brain Reasoning:", ignoreCase = true)) {
                            brainReasoningText.text = "🧠 $dataString"
                        }
                        if (dataString.contains("Strategy:", ignoreCase = true)) {
                            val strategy = dataString.substringAfter("Strategy:").trim()
                            strategyText.text = "🔧 Strategy: $strategy"
                        }

                        "[$timestamp] $text"
                    }
                    is Message.TaskComplete -> "[$timestamp] ${msg.from}: Task Completed"
                    is Message.ErrorOccurred -> "[$timestamp] ERROR ${msg.from}: ${msg.error.message}"
                    is Message.ResourcePreempted -> "[$timestamp] SYSTEM: ${msg.resource} preempted by ${msg.reason}"
                    else -> "[$timestamp] ${msg.from}: $msg"
                }
                appendLog(logEntry)
            }
        }
    }

    private fun appendLog(text: String) {
        val current = logText.text.toString()
        logText.text = "$current\n$text"
    }

    private fun executeCommand(command: String) {
        statusText.text = "Status: Processing '$command'..."
        logText.text = "--- New Task: $command ---"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Parse Intent
                val task = commandParser.parse(command)

                if (task == null) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Status: Unknown command. Try 'Go home', 'Read screen', 'Battery', 'Click...', 'Type...', 'Scroll...', or 'Back'."
                    }
                    return@launch
                }

                // 2. Build Graph
                val graph = TaskGraph()
                graph.addCompositeNode(task)

                // 3. Execute
                val executor = GraphExecutor(graph, resourceManager, messageBus, stateManager, agentGovernor)
                executor.execute()

                // 4. Report Result (Mocked: Check if graph completed)
                withContext(Dispatchers.Main) {
                    if (graph.isComplete()) {
                        statusText.text = "Status: Task Completed via Agent Swarm"
                        Toast.makeText(this@MainActivity, "Agent Execution Finished", Toast.LENGTH_SHORT).show()
                    } else {
                        statusText.text = "Status: Task Failed or Incomplete"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Status: Error - ${e.message}"
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkServiceStatus()
    }

    private fun checkServiceStatus() {
        if (ControllerService.instance == null) {
            statusText.text = "Status: Accessibility Service NOT Connected"
            settingsButton.isEnabled = true
        } else {
            statusText.text = "Status: Ready"
            // settingsButton.isEnabled = false // Optional: disable if already connected
        }
    }
}
