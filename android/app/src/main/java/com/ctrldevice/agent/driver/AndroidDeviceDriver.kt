package com.ctrldevice.agent.driver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.ctrldevice.service.accessibility.ControllerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidDeviceDriver(private val serviceProvider: () -> ControllerService?) : DeviceDriver {

    private val service: ControllerService?
        get() = serviceProvider()

    // --- Global Actions ---

    override suspend fun back(): Boolean {
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    }

    override suspend fun home(): Boolean {
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
    }

    override suspend fun openSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service?.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun openRecentApps(): Boolean {
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
    }

    override suspend fun getBatteryStatus(): BatteryInfo? {
        val context = service ?: return null
        return try {
            val iFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)

            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                0
            }

            val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL

            BatteryInfo(batteryPct, isCharging)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun setScreenOn(on: Boolean) {
        // Optimization: Use WakeLock to keep screen on if requested
        val context = service ?: return
        try {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (on) {
                // Acquire a temporary wakelock to ensure screen wakes up
                val wakeLock = pm.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "CtrlDevice:DriverWake"
                )
                wakeLock.acquire(3000) // Hold for 3s to ensure wake
                // Note: Long-term holding is managed by ResourceManager
            }
            // Turning screen OFF is generally not possible for standard apps without Admin API
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun openUrl(url: String): Boolean {
        val context = service ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun launchApp(packageName: String): Boolean {
        val context = service ?: return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun launchAppSearch(query: String): Boolean {
        // Generic intent to search in an app? Or global search?
        // Defaulting to Web Search via Intent.ACTION_WEB_SEARCH
        val context = service ?: return false
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(android.app.SearchManager.QUERY, query)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Gestures ---

    override suspend fun click(x: Float, y: Float): Boolean {
        return service?.dispatchTap(x, y) ?: false
    }

    override suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean {
        return service?.dispatchSwipe(startX, startY, endX, endY, durationMs) ?: false
    }

    override suspend fun takeScreenshot(): Bitmap? {
        val s = service ?: return null
        return suspendCancellableCoroutine { cont ->
            s.captureScreenshot { bitmap ->
                cont.resume(bitmap)
            }
        }
    }

    override fun getUiTree(): UiNode? {
        val root = service?.rootInActiveWindow ?: return null
        return try {
            mapNode(root)
        } finally {
             root.recycle()
        }
    }

    // --- Element Interactions ---

    // Logic Compression: Higher-order function to handle lifecycle (Root/Nodes/Recycle)
    private suspend fun <T> withFoundNodes(
        criteria: ElementCriteria,
        default: T,
        block: suspend (List<AccessibilityNodeInfo>) -> T
    ): T {
        val root = service?.rootInActiveWindow ?: return default
        var nodes: List<AccessibilityNodeInfo> = emptyList()
        try {
            nodes = findNodes(root, criteria)
            return block(nodes)
        } finally {
            nodes.forEach { it.recycle() }
            root.recycle()
        }
    }

    override suspend fun clickElement(criteria: ElementCriteria): Boolean {
        return withFoundNodes(criteria, false) { nodes ->
            for (node in nodes) {
                // traverseAncestors handles recycling of intermediates
                val clickable = traverseAncestors(node) { it.isClickable }
                if (clickable != null) {
                    try {
                        return@withFoundNodes clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } finally {
                        // Only recycle if it's a new parent object, not the node from the list
                        if (clickable != node) clickable.recycle()
                    }
                }
            }
            false
        }
    }

    override suspend fun inputText(criteria: ElementCriteria, text: String): Boolean {
        // inputText has unique fallback logic (findFocus), so we handle root manually but compress the rest
        val root = service?.rootInActiveWindow ?: return false
        var targetNode: AccessibilityNodeInfo? = null
        var nodes: List<AccessibilityNodeInfo> = emptyList()

        try {
            if (criteria.text == null && criteria.viewId == null && criteria.contentDescription == null) {
                targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                             ?: findEditableNodeRecursive(root)
            } else {
                nodes = findNodes(root, criteria)
                targetNode = nodes.firstOrNull()
            }

            if (targetNode == null) return false

            if (!targetNode.isFocused) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }

            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } finally {
            // Recycle targetNode if it wasn't from the nodes list
            if (targetNode != null && nodes.none { it == targetNode }) {
                targetNode.recycle()
            }
            nodes.forEach { it.recycle() }
            root.recycle()
        }
    }

    override suspend fun scroll(criteria: ElementCriteria, direction: ScrollDirection): Boolean {
        val root = service?.rootInActiveWindow ?: return false
        var nodes: List<AccessibilityNodeInfo> = emptyList()

        try {
            val action = when(direction) {
                ScrollDirection.FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                ScrollDirection.BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }

            // Logic Compression: Unify scroll target finding
            val scrollable = if (criteria.text != null || criteria.viewId != null) {
                nodes = findNodes(root, criteria)
                val target = nodes.firstOrNull() ?: return false
                traverseAncestors(target) { it.isScrollable }
            } else {
                findScrollableNodeRecursive(root)
            } ?: return false

            try {
                return scrollable.performAction(action)
            } finally {
                // Recycle if it's a separate object (not root, and not in nodes list)
                // traverseAncestors returns either 'target' (in nodes) or a parent (new)
                // findScrollableNodeRecursive returns a new copy/child
                val isFromNodes = nodes.any { it == scrollable }
                if (scrollable != root && !isFromNodes) {
                    scrollable.recycle()
                }
            }
        } finally {
            nodes.forEach { it.recycle() }
            root.recycle()
        }
    }

    override suspend fun findElement(criteria: ElementCriteria): UiElement? {
        return withFoundNodes(criteria, null) { nodes ->
            val node = nodes.firstOrNull() ?: return@withFoundNodes null
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            UiElement(
                text = node.text?.toString() ?: node.contentDescription?.toString(),
                bounds = bounds,
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isScrollable = node.isScrollable
            )
        }
    }

    override suspend fun waitForElement(criteria: ElementCriteria, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val found = withFoundNodes(criteria, false) { it.isNotEmpty() }
            if (found) return true
            delay(500)
        }
        return false
    }

    // --- Helpers ---

    private val MAX_SEARCH_DEPTH = 50

    private fun findNodes(root: AccessibilityNodeInfo, criteria: ElementCriteria): List<AccessibilityNodeInfo> {
        // 1. ID Match
        if (criteria.viewId != null) {
            val byId = root.findAccessibilityNodeInfosByViewId(criteria.viewId)
            if (byId.isNotEmpty()) return byId
        }

        // 2. Text Match
        if (criteria.text != null) {
            // Try standard API first
            val byText = root.findAccessibilityNodeInfosByText(criteria.text)
            if (byText.isNotEmpty()) return byText

            // Recursive fallback with depth limit
            val found = mutableListOf<AccessibilityNodeInfo>()
            findNodesByTextRecursive(root, criteria.text, criteria.matchSubstring, found, 0)
            if (found.isNotEmpty()) return found
        }

        return emptyList()
    }

    private fun findNodesByTextRecursive(
        node: AccessibilityNodeInfo,
        text: String,
        matchSubstring: Boolean,
        result: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (depth > MAX_SEARCH_DEPTH) return

        val nodeText = node.text?.toString()
        val nodeDesc = node.contentDescription?.toString()

        val match = if (matchSubstring) {
            (nodeText?.contains(text, ignoreCase = true) == true) || (nodeDesc?.contains(text, ignoreCase = true) == true)
        } else {
             (nodeText.equals(text, ignoreCase = true)) || (nodeDesc.equals(text, ignoreCase = true))
        }

        if (match) {
            // Obtain a fresh copy or reference?
            // AccessibilityNodeInfo.obtain(node) makes a copy.
            // But usually we just return the node if we are traversing.
            // HOWEVER, we are iterating children. 'node' here is a child from getChild(i).
            // It will be recycled by the caller of this recursive function loop... wait.

            // In the loop below: node.getChild(i)?.let { child -> find...(child) ... }
            // The child is effectively 'owned' by the recursive call.

            // To return it in a list, we should probably make a copy so the recursion can safely recycle the original 'child'.
            // OR we assume ownership transfer.
            // Let's use obtain() to be safe and return a clean copy for the list.
            result.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodesByTextRecursive(child, text, matchSubstring, result, depth + 1)
                child.recycle() // Clean up child after recursion
            }
        }
    }

    // Generic ancestor traversal that cleans up intermediates
    private fun traverseAncestors(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var current = node // We don't own node (owned by caller list), so we don't recycle it initially
        var steps = 0

        // Check self first
        if (predicate(current)) return current

        // Start traversing up
        var parent = current.parent
        while (parent != null && steps < MAX_SEARCH_DEPTH) {
            if (predicate(parent)) {
                return parent // Caller must recycle this if it's not the original 'node'
            }

            // Prepare for next step
            val grandParent = parent.parent

            // Recycle 'parent' as we are done with it
            parent.recycle()

            parent = grandParent
            steps++
        }
        return null
    }

    private fun findScrollableNodeRecursive(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        return findNodeRecursive(node, depth) { it.isScrollable }
    }

    private fun findEditableNodeRecursive(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        return findNodeRecursive(node, depth) { it.isEditable }
    }

    // Logic Compression: Generic recursive finder to replace specific implementations
    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        depth: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (depth > MAX_SEARCH_DEPTH) return null
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node) // Return copy

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val res = findNodeRecursive(child, depth + 1, predicate)
                child.recycle() // Recycle child wrapper
                if (res != null) return res
            }
        }
        return null
    }

    private fun mapNode(node: AccessibilityNodeInfo, depth: Int = 0): UiNode? {
        // Optimization: Prune deep trees to avoid stack overflow and huge payloads
        if (depth > MAX_SEARCH_DEPTH) {
            return null
        }

        val children = mutableListOf<UiNode>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val mappedChild = mapNode(child, depth + 1)
                if (mappedChild != null) {
                    children.add(mappedChild)
                }
                child.recycle() // Recycle child wrapper after mapping
            }
        }

        // Optimization: Context Pruning
        // Remove nodes that add no semantic value (no text, no ID, no interaction, no children)
        val hasText = !node.text.isNullOrBlank()
        val hasDesc = !node.contentDescription.isNullOrBlank()
        val hasId = !node.viewIdResourceName.isNullOrBlank()
        val isInteractive = node.isClickable || node.isScrollable || node.isEditable || node.isFocused

        // If it's a layout container (e.g. FrameLayout) with no ID/Text/Interaction and NO interesting children -> Prune
        if (!hasText && !hasDesc && !hasId && !isInteractive && children.isEmpty()) {
            return null
        }

        // Also, if it's just a wrapper with 1 child and no other properties, we *could* flatten,
        // but that might mess up coordinates for clicking parent. Let's stick to leaf pruning for now.

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Filter out tiny invisible elements?
        if (bounds.width() == 0 || bounds.height() == 0) {
            return null
        }

        return UiNode(
            text = node.text?.toString(),
            description = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            className = node.className?.toString(),
            bounds = bounds,
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isEditable = node.isEditable,
            isFocused = node.isFocused,
            children = children
        )
    }
}
