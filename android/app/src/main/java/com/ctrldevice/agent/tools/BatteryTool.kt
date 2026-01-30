package com.ctrldevice.agent.tools

import android.util.Log
import com.ctrldevice.agent.driver.DeviceDriver

/**
 * A tool to read the device's battery level and charging status.
 */
class BatteryTool(private val driver: DeviceDriver) : AgentTool {
    override val name = "check_battery"
    override val description = "Checks the current battery level and charging status."

    override suspend fun execute(params: String): ToolResult {
        Log.d("BatteryTool", "Executing Battery Check")

        val info = driver.getBatteryStatus() ?: return ToolResult(false, "Failed to read battery status")

        val chargeStatusStr = if (info.isCharging) "Charging" else "Discharging"
        val output = "Battery Level: ${info.levelPercent}%\nStatus: $chargeStatusStr"

        return ToolResult(true, output)
    }
}
