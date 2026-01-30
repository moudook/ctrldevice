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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.graphics.Color
import com.ctrldevice.features.agent_engine.coordination.*
import com.ctrldevice.service.accessibility.ControllerService
import com.ctrldevice.features.agent_engine.safety.AgentGovernor
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

import android.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ctrldevice.features.agent_engine.config.AppConfig
import com.ctrldevice.features.agent_engine.coordination.CurrentSession
import com.ctrldevice.ui.LogAdapter
import kotlinx.datetime.Clock

class MainActivity : Activity() {

    private lateinit var inputField: EditText
    private lateinit var executeButton: Button
    private lateinit var micFab: FloatingActionButton
    private lateinit var statusText: TextView
    private lateinit var settingsButton: Button
    private lateinit var stopButton: Button
    private lateinit var configButton: Button // New Config Button
    private lateinit var skillsButton: Button // Skills Manager Button
    private lateinit var overlayButton: Button // Overlay Button
    private lateinit var graphButton: Button // Graph Button
    private lateinit var logRecyclerView: RecyclerView
    private val logAdapter = LogAdapter()
    private lateinit var brainReasoningText: TextView
    private lateinit var strategyText: TextView
    private lateinit var thinkingProgress: ProgressBar

    private var speechRecognizer: SpeechRecognizer? = null

    // Agent Engine Components
    private val messageBus = MessageBus.Instance
    private lateinit var stateManager: StateManager
    private lateinit var resourceManager: ResourceManager
    private lateinit var agentGovernor: AgentGovernor
    private lateinit var appConfig: AppConfig // App Config
    private val ORCHESTRATOR_ID = "Orchestrator"
    private val REQUEST_CODE_WATCH_LEARN = 200
    private lateinit var commandParser: com.ctrldevice.features.agent_engine.parsing.CommandParser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize components that require Context
        stateManager = StateManager(applicationContext)
        resourceManager = ResourceManager(applicationContext, messageBus, stateManager)
        agentGovernor = AgentGovernor(stateManager)

        // Initialize Driver and Tools
        val driver = com.ctrldevice.agent.driver.AndroidDeviceDriver { ControllerService.instance }
        com.ctrldevice.agent.tools.ToolRegistry.initialize(driver)

        // Initialize Skill Registry
        com.ctrldevice.features.agent_engine.skills.SkillRegistry.initialize(applicationContext)

        // Initialize App Config
        appConfig = AppConfig(this)

        // Initialize Command Parser with Brain (if configured)
        updateCommandParser()

        inputField = findViewById(R.id.inputField)
        executeButton = findViewById(R.id.executeButton)
        micFab = findViewById(R.id.micFab)
        statusText = findViewById(R.id.statusText)
        settingsButton = findViewById(R.id.settingsButton)
        stopButton = findViewById(R.id.stopButton)
        configButton = findViewById(R.id.configButton)
        skillsButton = findViewById(R.id.skillsButton)
        overlayButton = findViewById(R.id.overlayButton)
        graphButton = findViewById(R.id.graphButton)
        logRecyclerView = findViewById(R.id.logRecyclerView)
        brainReasoningText = findViewById(R.id.brainReasoningText)
        strategyText = findViewById(R.id.strategyText)
        thinkingProgress = findViewById(R.id.thinkingProgress)

        // Setup RecyclerView
        logRecyclerView.layoutManager = LinearLayoutManager(this)
        logRecyclerView.adapter = logAdapter

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

        configButton.setOnClickListener {
            showConfigDialog()
        }

        skillsButton.setOnClickListener {
            val intent = Intent(this, com.ctrldevice.ui.macros.MacroListActivity::class.java)
            startActivity(intent)
        }

        overlayButton.setOnClickListener {
            checkOverlayPermissionAndStart()
        }

        graphButton.setOnClickListener {
            val intent = Intent(this, com.ctrldevice.ui.graph.GraphActivity::class.java)
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
                    addSystemLog("SAFETY", "User touch detected. Emergency Stop Triggered.")
                    Toast.makeText(this@MainActivity, "Agent Paused by Touch", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun subscribeToAgentThoughts() {
        val channel = messageBus.register(ORCHESTRATOR_ID)
        CoroutineScope(Dispatchers.Main).launch {
            for (msg in channel) {
                // DASHBOARD UPDATES
                if (msg is Message.DataAvailable) {
                    val dataString = msg.data.toString()
                    if (dataString.contains("Brain Reasoning:", ignoreCase = true)) {
                        brainReasoningText.text = "🧠 $dataString"
                    }
                    if (dataString.contains("Strategy:", ignoreCase = true)) {
                        val strategy = dataString.substringAfter("Strategy:").trim()
                        strategyText.text = "🔧 Strategy: $strategy"
                    }
                } else if (msg is Message.UserInterventionNeeded) {
                    showInterventionDialog(msg.reason)
                }

                logAdapter.addMessage(msg)
                logRecyclerView.scrollToPosition(logAdapter.itemCount - 1)
            }
        }
    }

    private fun showInterventionDialog(reason: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Agent Needs Help")
        builder.setMessage("$reason\n\nHow do you want to proceed?")
        builder.setCancelable(false) // User must decide

        builder.setPositiveButton("Resume/Retry") { dialog, _ ->
            agentGovernor.resumeFromIntervention()
            addSystemLog("User", "Instructed Agent to Resume/Retry.")
            dialog.dismiss()
        }

        builder.setNegativeButton("Stop Agent") { dialog, _ ->
            agentGovernor.triggerEmergencyStop()
            addSystemLog("User", "Stopped Agent via Intervention.")
            dialog.dismiss()
        }

        // In the future, we can add a "Watch Me" button here for the "Show me how" feature
        builder.setNeutralButton("Manual Help (Watch Me)") { dialog, _ ->
            // Don't resume yet. Wait for the user to finish recording.
            addSystemLog("User", "Starting Watch & Learn session...")

            val intent = Intent(this, com.ctrldevice.ui.macros.MacroEditorActivity::class.java)
            intent.putExtra("AUTO_RECORD", true)
            startActivityForResult(intent, REQUEST_CODE_WATCH_LEARN)

            dialog.dismiss()
        }

        builder.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) { // Overlay Permission
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "Overlay Permission Denied", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQUEST_CODE_WATCH_LEARN) {
            if (resultCode == Activity.RESULT_OK) {
                addSystemLog("User", "Watch & Learn session completed. Resuming agent...")
                Toast.makeText(this, "Agent Resuming...", Toast.LENGTH_SHORT).show()
                agentGovernor.resumeFromIntervention()
            } else {
                addSystemLog("User", "Watch & Learn session cancelled. Resuming agent anyway.")
                agentGovernor.resumeFromIntervention()
            }
        }
    }

    private fun addSystemLog(from: String, text: String) {
        val msg = Message.DataAvailable(
            from = from,
            to = "User",
            timestamp = Clock.System.now(),
            key = "log",
            data = text
        )
        logAdapter.addMessage(msg)
        logRecyclerView.scrollToPosition(logAdapter.itemCount - 1)
    }

    private fun executeCommand(command: String) {
        statusText.text = "Status: Processing '$command'..."
        statusText.setTextColor(Color.parseColor("#1976D2")) // Blue
        thinkingProgress.visibility = View.VISIBLE

        logAdapter.clear()
        addSystemLog("System", "--- New Task: $command ---")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Parse Intent
                val task = commandParser.parse(command)

                if (task == null) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Status: Unknown command. Try 'Go home', 'Read screen', 'Battery', 'Click...', 'Type...', 'Scroll...', or 'Back'."
                        statusText.setTextColor(Color.RED)
                        thinkingProgress.visibility = View.INVISIBLE
                    }
                    return@launch
                }

                // 2. Build Graph
                val graph = TaskGraph()
                graph.addCompositeNode(task)

                // Update Session
                CurrentSession.activeGraph = graph

                // 3. Execute
                val executor = GraphExecutor(graph, resourceManager, messageBus, stateManager, agentGovernor, appConfig)
                CurrentSession.activeExecutor = executor
                executor.execute()

                // 4. Report Result (Mocked: Check if graph completed)
                withContext(Dispatchers.Main) {
                    thinkingProgress.visibility = View.INVISIBLE
                    if (graph.isComplete()) {
                        statusText.text = "Status: Task Completed via Agent Swarm"
                        statusText.setTextColor(Color.parseColor("#388E3C")) // Green
                        Toast.makeText(this@MainActivity, "Agent Execution Finished", Toast.LENGTH_SHORT).show()
                    } else {
                        statusText.text = "Status: Task Failed or Incomplete"
                        statusText.setTextColor(Color.RED)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    thinkingProgress.visibility = View.INVISIBLE
                    statusText.text = "Status: Error - ${e.message}"
                    statusText.setTextColor(Color.RED)
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkServiceStatus()
    }

    private fun checkOverlayPermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
            startActivityForResult(intent, 101)
            Toast.makeText(this, "Please grant Overlay Permission", Toast.LENGTH_LONG).show()
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, com.ctrldevice.service.overlay.OverlayService::class.java)
        startService(intent)
        // minimizeApp() // Optional: Go home automatically?
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

    private fun showConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_config, null)
        val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.brainTypeSpinner)
        val apiKeyInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.apiKeyInput)
        val endpointInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.endpointInput)
        val modelInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.modelInput)
        val exportButton = dialogView.findViewById<android.widget.Button>(R.id.exportDataButton)

        // Setup Spinner
        val brainTypes = arrayOf("Rule Based (Default)", "LLM (Remote)", "LLM (Local Network)")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, brainTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Setup Export Button
        exportButton.setOnClickListener {
            val zipFile = com.ctrldevice.utils.DataExporter.exportUserData(this)
            if (zipFile != null) {
                val uri = com.ctrldevice.utils.DataExporter.getUriForFile(this, zipFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Export Training Data"))
            } else {
                Toast.makeText(this, "Export Failed: No data or error occurred", Toast.LENGTH_SHORT).show()
            }
        }

        // Load current settings
        spinner.setSelection(when (appConfig.brainType) {
            AppConfig.BRAIN_LLM_REMOTE -> 1
            AppConfig.BRAIN_LLM_LOCAL -> 2
            else -> 0
        })
        apiKeyInput.setText(appConfig.llmApiKey)
        endpointInput.setText(appConfig.llmEndpoint)
        modelInput.setText(appConfig.llmModel)

        AlertDialog.Builder(this)
            .setTitle("Agent Configuration")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val selectedType = when (spinner.selectedItemPosition) {
                    1 -> AppConfig.BRAIN_LLM_REMOTE
                    2 -> AppConfig.BRAIN_LLM_LOCAL
                    else -> AppConfig.BRAIN_RULE_BASED
                }
                appConfig.brainType = selectedType
                appConfig.llmApiKey = apiKeyInput.text.toString()
                appConfig.llmEndpoint = endpointInput.text.toString()
                appConfig.llmModel = modelInput.text.toString()

                Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
                updateDashboardWithConfig()
                updateCommandParser()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateDashboardWithConfig() {
        when (appConfig.brainType) {
            AppConfig.BRAIN_LLM_REMOTE -> brainReasoningText.text = "🧠 Brain: LLM (Remote - ${appConfig.llmModel})"
            AppConfig.BRAIN_LLM_LOCAL -> brainReasoningText.text = "🧠 Brain: LLM (Local - ${appConfig.llmModel})"
            else -> brainReasoningText.text = "🧠 Brain: Rule Based"
        }
    }

    private fun updateCommandParser() {
        val brain = when (appConfig.brainType) {
            AppConfig.BRAIN_LLM_REMOTE, AppConfig.BRAIN_LLM_LOCAL -> {
                com.ctrldevice.features.agent_engine.intelligence.LLMBrain(
                    appConfig.llmApiKey,
                    appConfig.llmEndpoint,
                    appConfig.llmModel
                )
            }
            else -> null
        }
        commandParser = com.ctrldevice.features.agent_engine.parsing.CommandParser(brain)
    }
}
