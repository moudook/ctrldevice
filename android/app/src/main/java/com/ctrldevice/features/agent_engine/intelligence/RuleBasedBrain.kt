package com.ctrldevice.features.agent_engine.intelligence

import com.ctrldevice.features.agent_engine.coordination.TaskNode

/**
 * A heuristic-based brain that uses regex and keyword matching to decide actions.
 * Acts as the default fallback when no LLM is available.
 */
class RuleBasedBrain : AgentBrain {

    // Regex Patterns for clean extraction (mirrors CommandParser)
    private val CLICK_REGEX = Regex("""(?i)^click\s+(?:on\s+)?(.+)$""")
    private val TYPE_REGEX = Regex("""(?i)^type\s+(.+?)(?:\s+into\s+(.+))?$""")
    private val SCROLL_REGEX = Regex("""(?i)^scroll\s+(.+)$""")
    private val SEARCH_REGEX = Regex("""(?i)^(?:search(?:\s+for)?|browse)\s+(.+)$""")
    private val OPEN_REGEX = Regex("""(?i)^(?:open|launch)\s+(.+)$""")
    private val SWIPE_REGEX = Regex("""(?i)^swipe\s+(.+)$""")
    private val TAP_REGEX = Regex("""(?i)^tap\s+(.+)$""")

    override suspend fun proposeNextStep(
        task: TaskNode.AtomicTask,
        screenContext: String,
        previousActions: List<String>
    ): BrainThought {
        val description = task.description
        val lowerDesc = description.lowercase()

        // 1. Check for explicit strategies/recoveries in the description
        // (In the future, the Brain determines strategies, but for now we map task to tool)

        // Try Regex matching first for complex commands
        CLICK_REGEX.find(description)?.let { match ->
            val target = match.groupValues[1].trim()

            if (screenContext.isBlank()) {
                return BrainThought(
                    reasoning = "Screen context is empty. I cannot safely click '$target' without knowing what's on screen. I should try to read the screen again.",
                    toolName = "read_screen",
                    toolParams = "{}"
                )
            }

            return if (!screenContext.contains(target, ignoreCase = true)) {
                BrainThought(
                    reasoning = "Target '$target' is not visible on screen. I should scroll down to find it.",
                    toolName = "scroll",
                    toolParams = "down"
                )
            } else {
                BrainThought(
                    reasoning = "User asked to click '$target'.",
                    toolName = "click_element",
                    toolParams = target
                )
            }
        }

        TYPE_REGEX.find(description)?.let { match ->
            val text = match.groupValues[1].trim()
            val field = match.groupValues.getOrNull(2)?.trim()

            if (screenContext.isBlank() && field != null) {
                return BrainThought(
                    reasoning = "Screen context is empty. I cannot safely type into '$field' without knowing where it is.",
                    toolName = "read_screen",
                    toolParams = "{}"
                )
            }
            val reasoning = if (field != null) "User asked to type '$text' into '$field'." else "User asked to type '$text'."

            val toolParams = if (field != null) "$text into $field" else text

            return BrainThought(
                reasoning = reasoning,
                toolName = "input_text",
                toolParams = toolParams
            )
        }

        SCROLL_REGEX.find(description)?.let { match ->
            val dir = match.groupValues[1].trim()
            return BrainThought(
                reasoning = "User asked to scroll '$dir'.",
                toolName = "scroll",
                toolParams = dir
            )
        }

        SEARCH_REGEX.find(description)?.let { match ->
            val query = match.groupValues[1].trim()
            return BrainThought(
                reasoning = "User asked to search for '$query'.",
                toolName = "open_chrome",
                toolParams = query
            )
        }

        OPEN_REGEX.find(description)?.let { match ->
            val appName = match.groupValues[1].trim()
            return BrainThought(
                reasoning = "User asked to launch app '$appName'.",
                toolName = "launch_app",
                toolParams = appName
            )
        }

        SWIPE_REGEX.find(description)?.let { match ->
            val swipeParams = match.groupValues[1].trim()
            return BrainThought(
                reasoning = "User asked to swipe '$swipeParams'.",
                toolName = "gesture",
                toolParams = "swipe $swipeParams"
            )
        }

        TAP_REGEX.find(description)?.let { match ->
            val tapParams = match.groupValues[1].trim()
            return BrainThought(
                reasoning = "User asked to tap at '$tapParams'.",
                toolName = "gesture",
                toolParams = "tap $tapParams"
            )
        }

        // Fallback for simple keywords if regex didn't match
        return when {
            lowerDesc.contains("home") -> {
                BrainThought(
                    reasoning = "User asked to go home.",
                    toolName = "go_home",
                    toolParams = "{}"
                )
            }
            lowerDesc.contains("settings") -> {
                BrainThought(
                    reasoning = "User asked to open settings.",
                    toolName = "open_settings",
                    toolParams = "{}"
                )
            }
            lowerDesc.contains("back") -> {
                BrainThought(
                    reasoning = "User asked to go back.",
                    toolName = "go_back",
                    toolParams = "{}"
                )
            }
            lowerDesc.contains("battery") -> {
                BrainThought(
                    reasoning = "User asked to check battery.",
                    toolName = "check_battery",
                    toolParams = "{}"
                )
            }
            lowerDesc.contains("screen") || lowerDesc.contains("read") -> {
                BrainThought(
                    reasoning = "User asked to read screen content.",
                    toolName = "read_screen",
                    toolParams = "{}"
                )
            }
            // All complex commands handled by Regex above
            else -> {
                BrainThought(
                    reasoning = "I don't know how to handle this task yet.",
                    toolName = "unknown",
                    toolParams = "{}"
                )
            }
        }
    }
}
