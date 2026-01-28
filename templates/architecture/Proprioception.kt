package com.ctrldevice.features.agent_engine.senses

import com.ctrldevice.service.accessibility.ControllerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The "Nervous System" of the Agent.
 * Streams the device's physical state directly into the Agent's consciousness.
 */
data class BodyState(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val networkType: NetworkType,
    val screenOn: Boolean,
    val currentAppPackage: String,
    val userInterrupted: Boolean // Added for safety
)

class ProprioceptionSystem(
    private val batterySensor: Flow<Int>,
    private val chargingSensor: Flow<Boolean>,
    private val networkSensor: Flow<NetworkType>,
    private val screenSensor: Flow<Boolean>,
    private val appSensor: Flow<String>
) {

    /**
     * A continuous stream of "Self-Awareness".
     * The Orchestrator subscribes to this.
     */
    val consciousness: Flow<BodyState> = combine(
        batterySensor,
        chargingSensor,
        networkSensor,
        screenSensor,
        appSensor,
        ControllerService.userInterrupts // Inject real-time safety stream
    ) { battery, charging, net, screen, app, interrupt ->
        BodyState(battery, charging, net, screen, app, interrupt)
    }
}

enum class NetworkType {
    WIFI, CELLULAR, OFFLINE
}