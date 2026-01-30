package com.ctrldevice.agent.driver

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Abstraction layer for device interactions.
 * Decouples the Agent/Tools from the Android Framework (AccessibilityService).
 */
interface DeviceDriver {
    // Global Actions
    suspend fun back(): Boolean
    suspend fun home(): Boolean
    suspend fun openSettings(): Boolean
    suspend fun openRecentApps(): Boolean
    suspend fun setScreenOn(on: Boolean)
    suspend fun openUrl(url: String): Boolean
    suspend fun launchApp(packageName: String): Boolean
    suspend fun launchAppSearch(query: String): Boolean
    suspend fun getBatteryStatus(): BatteryInfo?

    // Gestures
    suspend fun click(x: Float, y: Float): Boolean
    suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean

    // Element Interactions (Accessibility)
    suspend fun clickElement(criteria: ElementCriteria): Boolean
    suspend fun inputText(criteria: ElementCriteria, text: String): Boolean
    suspend fun scroll(criteria: ElementCriteria, direction: ScrollDirection): Boolean
    suspend fun findElement(criteria: ElementCriteria): UiElement?
    suspend fun waitForElement(criteria: ElementCriteria, timeoutMs: Long): Boolean

    // Inspection
    suspend fun takeScreenshot(): Bitmap?
    fun getUiTree(): UiNode?
}

data class ElementCriteria(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val matchSubstring: Boolean = true
)

enum class ScrollDirection { FORWARD, BACKWARD, LEFT, RIGHT }

data class UiElement(
    val text: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean
)

data class UiNode(
    val text: String?,
    val description: String?,
    val viewId: String?,
    val className: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean,
    val isFocused: Boolean,
    val children: List<UiNode>
)
