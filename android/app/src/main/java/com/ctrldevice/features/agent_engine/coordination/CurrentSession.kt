package com.ctrldevice.features.agent_engine.coordination

/**
 * Singleton to hold the current active session state for UI visualization.
 * Avoids complex Intent serialization.
 */
object CurrentSession {
    var activeGraph: TaskGraph? = null
    var activeExecutor: GraphExecutor? = null
}
