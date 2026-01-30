package com.ctrldevice.features.agent_engine.skills

import android.view.accessibility.AccessibilityEvent
import com.ctrldevice.service.accessibility.ControllerService
import com.ctrldevice.service.accessibility.SafeAccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

import java.util.concurrent.atomic.AtomicBoolean
import java.util.Collections

/**
 * Records user actions (via AccessibilityEvents) and converts them into a Macro.
 */
class MacroRecorder {
    // Optimization: Thread-safe list for recording events from background thread
    private val recordedCommands = Collections.synchronizedList(mutableListOf<String>())

    // Optimization: Atomic flag for visibility across threads
    private val isRecording = AtomicBoolean(false)

    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startRecording() {
        if (isRecording.getAndSet(true)) return // Already recording

        recordedCommands.clear()

        recordingJob = scope.launch {
            ControllerService.screenEvents.collect { event ->
                if (!isRecording.get()) return@collect

                // Convert SafeAccessibilityEvent to a command string
                val command = processEvent(event)
                if (command != null) {
                    // Avoid duplicate rapid-fire events
                    // Synchronize for atomic check-then-add on the list content
                    synchronized(recordedCommands) {
                        if (recordedCommands.isEmpty() || recordedCommands.last() != command) {
                            recordedCommands.add(command)
                        }
                    }
                }
            }
        }
    }

    fun stopRecording(): List<String> {
        isRecording.set(false)
        recordingJob?.cancel()

        // Return a copy to avoid concurrent modification issues downstream
        synchronized(recordedCommands) {
            return recordedCommands.toList()
        }
    }

    private fun processEvent(event: SafeAccessibilityEvent): String? {
        // This is a heuristic translation.
        // Real-world would need robust element ID/text extraction.

        return when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val text = event.text.joinToString(" ")
                if (text.isNotBlank()) {
                    "click \"$text\""
                } else if (event.contentDescription != null) {
                    "click \"${event.contentDescription}\""
                } else {
                    "click element" // Fallback
                }
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val text = event.text.joinToString(" ")
                 if (text.isNotBlank()) {
                    "long_click \"$text\""
                } else {
                    "long_click element"
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Heuristic: capture scroll
                "scroll"
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.joinToString(" ")
                if (text.isNotBlank()) {
                     "type \"$text\""
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
