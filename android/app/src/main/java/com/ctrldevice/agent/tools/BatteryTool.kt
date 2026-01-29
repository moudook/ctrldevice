package com.ctrldevice.agent.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.ctrldevice.service.accessibility.ControllerService

/**
 * A tool to read the device's battery level and charging status.
 * This adds internal state awareness (Interoception) to the agent.
 */
class BatteryTool : AgentTool {
    override val name = "check_battery"
    override val description = "Checks the current battery level and charging status."

    override suspend fun execute(params: String): ToolResult {
        Log.d("BatteryTool", "Executing Battery Check")

        val service = ControllerService.instance
        if (service == null) {
            return ToolResult(false, "Accessibility Service not connected (needed for Context).")
        }

        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = service.registerReceiver(null, intentFilter)

            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val batteryPct = if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                -1
            }

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL

            val chargeStatusStr = if (isCharging) "Charging" else "Discharging"

            val output = "Battery Level: $batteryPct%\nStatus: $chargeStatusStr"

            ToolResult(true, output)
        } catch (e: Exception) {
            ToolResult(false, "Failed to read battery status: ${e.message}")
        }
    }
}
