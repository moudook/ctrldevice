package com.ctrldevice.features.agent_engine.intelligence

import com.ctrldevice.features.agent_engine.coordination.TaskNode
import com.ctrldevice.features.agent_engine.parsing.CommandIntent
import com.ctrldevice.features.agent_engine.parsing.IntentParser

/**
 * A heuristic-based brain that uses direct pattern matching to decide actions.
 * Optimized for O(1) dispatch using Kotlin's 'when' expression on sealed classes.
 * Logic compressed to remove redundant object allocations and virtual calls.
 */
class RuleBasedBrain : AgentBrain {

    override suspend fun proposeNextStep(
        task: TaskNode.AtomicTask,
        screenContext: String,
        previousActions: List<String>,
        screenshotBase64: String?,
        inputData: Map<String, Any>
    ): BrainThought {
        // Optimization: Use pre-parsed intent if available to avoid redundant regex processing
        val intent = task.intent ?: IntentParser.parse(task.description)

        return handleIntent(intent, screenContext)
    }

    private fun handleIntent(intent: CommandIntent, screenContext: String): BrainThought {
        return when (intent) {
            is CommandIntent.Click -> {
                val target = intent.target
                if (screenContext.isBlank()) {
                    BrainThought(
                        reasoning = "Screen context is empty. I cannot safely click '$target'. Reading screen first.",
                        toolName = "read_screen",
                        toolParams = "{}"
                    )
                } else if (!screenContext.contains(target, ignoreCase = true)) {
                    BrainThought(
                        reasoning = "Target '$target' not visible. Scrolling down.",
                        toolName = "scroll",
                        toolParams = "down"
                    )
                } else {
                    BrainThought("User asked to click '$target'.", "click_element", target)
                }
            }

            is CommandIntent.Input -> {
                val text = intent.text
                val field = intent.field

                if (screenContext.isBlank() && field != null) {
                    BrainThought(
                        reasoning = "Screen context empty. Need to find '$field' before typing.",
                        toolName = "read_screen",
                        toolParams = "{}"
                    )
                } else {
                    val params = if (field != null) "$text into $field" else text
                    BrainThought("Typing '$text'${if(field!=null) " into '$field'" else ""}.", "input_text", params)
                }
            }

            is CommandIntent.Scroll -> BrainThought("Scrolling ${intent.direction}.", "scroll", intent.direction)

            is CommandIntent.Search -> BrainThought("Searching for '${intent.query}'.", "open_chrome", intent.query)

            is CommandIntent.OpenApp -> BrainThought("Launching '${intent.appName}'.", "launch_app", intent.appName)

            is CommandIntent.Gesture -> BrainThought("Performing ${intent.type}.", "gesture", "${intent.type} ${intent.params}")

            is CommandIntent.FindElement -> BrainThought("Finding element '${intent.query}'.", "find_element", intent.query)

            is CommandIntent.WaitFor -> BrainThought("Waiting for '${intent.query}'.", "wait_for_element", intent.query)

            is CommandIntent.GlobalAction -> {
                when (intent.action) {
                    CommandIntent.GlobalActionType.HOME -> BrainThought("Going home.", "go_home", "{}")
                    CommandIntent.GlobalActionType.BACK -> BrainThought("Going back.", "go_back", "{}")
                    CommandIntent.GlobalActionType.SETTINGS -> BrainThought("Opening settings.", "open_settings", "{}")
                    CommandIntent.GlobalActionType.BATTERY -> BrainThought("Checking battery.", "check_battery", "{}")
                    CommandIntent.GlobalActionType.SCREEN_READ -> BrainThought("Reading screen.", "read_screen", "{}")
                    else -> BrainThought("Unknown global action.", "unknown", "{}")
                }
            }

            is CommandIntent.Macro -> BrainThought("Executing macro '${intent.name}'.", "macro", intent.name)

            // Fallback for recursive/wrapper types or unknown
            is CommandIntent.Schedule -> handleIntent(intent.intent, screenContext) // Unwrap schedule immediately if it reached here

            else -> BrainThought("Unknown intent.", "unknown", "{}")
        }
    }
}
