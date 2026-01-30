package com.ctrldevice.features.agent_engine.parsing

/**
 * Represents the structured intent behind a raw text command.
 */
sealed class CommandIntent {
    data class Click(val target: String) : CommandIntent()
    data class Input(val text: String, val field: String?) : CommandIntent()
    data class Scroll(val direction: String) : CommandIntent()
    data class Search(val query: String) : CommandIntent()
    data class OpenApp(val appName: String) : CommandIntent()
    data class Gesture(val type: String, val params: String) : CommandIntent()
    data class FindElement(val query: String) : CommandIntent()
    data class WaitFor(val query: String) : CommandIntent()
    data class Schedule(val intent: CommandIntent, val delayMs: Long) : CommandIntent()

    // System / Navigation
    data class GlobalAction(val action: GlobalActionType) : CommandIntent()
    enum class GlobalActionType { HOME, BACK, SETTINGS, BATTERY, SCREEN_READ, RECENT_APPS, LOCK_SCREEN }

    // Macro / Complex
    data class Macro(val name: String) : CommandIntent()

    // Fallback
    data class Unknown(val rawCommand: String) : CommandIntent()
    object Empty : CommandIntent()
}

/**
 * Centralized parser that converts raw user text into semantic [CommandIntent]s.
 * Handles regex matching, edge cases, and normalization.
 */
object IntentParser {

    private val CLICK_REGEX = Regex("""(?i)^click\s+(?:on\s+)?(.+)$""")
    private val TYPE_REGEX = Regex("""(?i)^type\s+(.+?)(?:\s+into\s+(.+))?$""")
    private val SCROLL_REGEX = Regex("""(?i)^scroll\s+(.+)$""")
    private val SEARCH_REGEX = Regex("""(?i)^(?:search(?:\s+for)?|browse)\s+(.+)$""")
    private val OPEN_REGEX = Regex("""(?i)^(?:open|launch)\s+(.+)$""")
    private val SWIPE_REGEX = Regex("""(?i)^swipe\s+(.+)$""")
    private val TAP_REGEX = Regex("""(?i)^tap\s+(.+)$""")
    private val FIND_REGEX = Regex("""(?i)^find\s+(?:element\s+)?(.+)$""")
    private val WAIT_REGEX = Regex("""(?i)^wait\s+(?:for\s+)?(.+)$""")
    private val SCHEDULE_REGEX = Regex("""(?i)^schedule\s+(.+)\s+in\s+(\d+)\s+(seconds?|minutes?|hours?)$""")

    fun parse(input: String?): CommandIntent {
        if (input.isNullOrBlank()) return CommandIntent.Empty
        val command = input.trim()

        // logic compression: O(1) dispatch based on first token
        // This acts like a K-map simplification: grouping common prefixes
        val firstSpace = command.indexOf(' ')
        val firstWord = if (firstSpace == -1) command else command.substring(0, firstSpace)
        val args = if (firstSpace == -1) "" else command.substring(firstSpace + 1).trim()

        return when (firstWord.lowercase()) {
            "click" -> {
                // Handle "click on <target>" vs "click <target>"
                val target = if (args.startsWith("on ", ignoreCase = true)) args.substring(3).trim() else args
                if (target.isNotEmpty()) CommandIntent.Click(target) else CommandIntent.Unknown(command)
            }
            "type" -> {
                // "type <text> into <field>"
                val intoIndex = args.lastIndexOf(" into ", ignoreCase = true)
                if (intoIndex != -1) {
                    val text = args.substring(0, intoIndex).trim()
                    val field = args.substring(intoIndex + 6).trim() // 6 is length of " into "
                    CommandIntent.Input(text, field)
                } else if (args.isNotEmpty()) {
                    CommandIntent.Input(args, null)
                } else {
                    CommandIntent.Unknown(command)
                }
            }
            "scroll" -> if (args.isNotEmpty()) CommandIntent.Scroll(args) else CommandIntent.Unknown(command)
            "search", "browse" -> if (args.isNotEmpty()) CommandIntent.Search(args) else CommandIntent.Unknown(command)
            "open", "launch" -> if (args.isNotEmpty()) CommandIntent.OpenApp(args) else CommandIntent.Unknown(command)
            "swipe" -> if (args.isNotEmpty()) CommandIntent.Gesture("swipe", args) else CommandIntent.Unknown(command)
            "tap" -> if (args.isNotEmpty()) CommandIntent.Gesture("tap", args) else CommandIntent.Unknown(command)
            "find" -> {
                 // "find element <query>" or "find <query>"
                 val query = if (args.startsWith("element ", ignoreCase = true)) args.substring(8).trim() else args
                 if (query.isNotEmpty()) CommandIntent.FindElement(query) else CommandIntent.Unknown(command)
            }
            "wait" -> {
                // "wait for <query>" or "wait <query>"
                val query = if (args.startsWith("for ", ignoreCase = true)) args.substring(4).trim() else args
                if (query.isNotEmpty()) CommandIntent.WaitFor(query) else CommandIntent.Unknown(command)
            }
            "schedule" -> {
                // "schedule <cmd> in <amount> <unit>"
                // We can use a simpler regex here or string parsing since we know the prefix
                SCHEDULE_REGEX.matchEntire(command)?.let { match ->
                    val subCommandText = match.groupValues[1]
                    val amount = match.groupValues[2].toLongOrNull() ?: 0L
                    val unit = match.groupValues[3].lowercase()
                    val delayMs = when {
                        unit.startsWith("minute") -> amount * 60 * 1000
                        unit.startsWith("hour") -> amount * 60 * 60 * 1000
                        else -> amount * 1000
                    }
                    val subIntent = parse(subCommandText)
                    if (subIntent !is CommandIntent.Unknown && subIntent !is CommandIntent.Empty) {
                         CommandIntent.Schedule(subIntent, delayMs)
                    } else null
                } ?: CommandIntent.Unknown(command)
            }
            // Navigation / Global shortcuts
            "home" -> CommandIntent.GlobalAction(CommandIntent.GlobalActionType.HOME)
            "back" -> CommandIntent.GlobalAction(CommandIntent.GlobalActionType.BACK)
            "settings" -> CommandIntent.GlobalAction(CommandIntent.GlobalActionType.SETTINGS)
            "battery" -> CommandIntent.GlobalAction(CommandIntent.GlobalActionType.BATTERY)
            "go" -> {
                when (args.lowercase()) {
                    "home" -> CommandIntent.GlobalAction(CommandIntent.GlobalActionType.HOME)
                    "back" -> CommandIntent.GlobalAction(CommandIntent.GlobalActionType.BACK)
                    else -> CommandIntent.Unknown(command)
                }
            }
            "read" -> if (args == "screen") CommandIntent.GlobalAction(CommandIntent.GlobalActionType.SCREEN_READ) else CommandIntent.Unknown(command)
            "system" -> if (args.equals("check", ignoreCase = true)) CommandIntent.Macro("system_check") else CommandIntent.Unknown(command)
            else -> {
                // Final fallback for multi-word commands not starting with standard verbs
                if (command.contains("battery", ignoreCase = true)) CommandIntent.GlobalAction(CommandIntent.GlobalActionType.BATTERY)
                else if (command.contains("screen", ignoreCase = true)) CommandIntent.GlobalAction(CommandIntent.GlobalActionType.SCREEN_READ)
                else CommandIntent.Unknown(command)
            }
        }
    }
}
