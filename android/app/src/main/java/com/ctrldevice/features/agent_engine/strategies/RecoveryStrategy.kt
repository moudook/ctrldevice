package com.ctrldevice.features.agent_engine.strategies

/**
 * Represents a specific recovery strategy to attempt when a task fails.
 */
sealed class RecoveryStrategy(val name: String) {
    object None : RecoveryStrategy("NONE")
    object Retry : RecoveryStrategy("RETRY")
    object ScrollDown : RecoveryStrategy("SCROLL_DOWN")
    object Wait : RecoveryStrategy("WAIT") // Wait for UI to settle
    object Back : RecoveryStrategy("BACK") // Go back and try again

    // Future: specialized strategies
    // data class Custom(val command: String) : RecoveryStrategy("CUSTOM")
}
