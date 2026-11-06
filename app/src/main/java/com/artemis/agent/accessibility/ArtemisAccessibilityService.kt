package com.artemis.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.artemis.agent.model.GlobalActionType
import com.artemis.agent.model.UiNode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ArtemisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ArtemisAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }

    private val cachedNodes = mutableListOf<AccessibilityNodeInfo>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are monitored if needed for live state tracking
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        clearCachedNodes()
    }

    private fun clearCachedNodes() {
        cachedNodes.forEach { runCatching { it.recycle() } }
        cachedNodes.clear()
    }

    /**
     * Dumps the visible accessibility node tree into a compact list of UiNodes.
     */
    fun dumpHierarchy(): List<UiNode> {
        clearCachedNodes()
        val root = rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<UiNode>()
        val bounds = Rect()

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 20 || result.size >= 120) return
            
            node.getBoundsInScreen(bounds)
            // Filter out off-screen or empty bounds
            if (bounds.width() > 0 && bounds.height() > 0 && node.isVisibleToUser) {
                val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
                val isInteractive = node.isClickable || node.isEditable || node.isScrollable

                if (hasText || isInteractive) {
                    val index = result.size
                    cachedNodes.add(AccessibilityNodeInfo.obtain(node))
                    result.add(
                        UiNode(
                            index = index,
                            text = node.text?.toString(),
                            contentDescription = node.contentDescription?.toString(),
                            className = node.className?.toString() ?: "",
                            packageName = node.packageName?.toString() ?: "",
                            bounds = Rect(bounds),
                            isClickable = node.isClickable,
                            isEditable = node.isEditable,
                            isScrollable = node.isScrollable
                        )
                    )
                }
            }

            for (i in 0 until node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull()
                traverse(child, depth + 1)
            }
        }

        traverse(root, 0)
        return result
    }

    /**
     * Clicks a specific node by its index in the dumped hierarchy.
     */
    fun clickNodeByIndex(index: Int): Boolean {
        val node = cachedNodes.getOrNull(index) ?: return false
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        // Fallback to coordinate tap
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return tapAtSync(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    /**
     * Dispatches a tap gesture at the specified screen coordinates.
     */
    suspend fun tapAt(x: Float, y: Float): Boolean = suspendCancellableCoroutine { continuation ->
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(false)
            }
        }, null)

        if (!dispatched && continuation.isActive) {
            continuation.resume(false)
        }
    }

    private fun tapAtSync(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Dispatches a smooth swipe gesture from start coordinates to end coordinates.
     */
    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(50L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(false)
            }
        }, null)

        if (!dispatched && continuation.isActive) {
            continuation.resume(false)
        }
    }

    /**
     * Injects text into currently focused editable field or sets text via Accessibility.
     */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootInActiveWindow?.let { findFirstEditable(it) }

        if (focused != null) {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        return false
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val found = findFirstEditable(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Executes standard system navigation gestures.
     */
    fun performGlobal(type: GlobalActionType): Boolean {
        val actionId = when (type) {
            GlobalActionType.BACK -> GLOBAL_ACTION_BACK
            GlobalActionType.HOME -> GLOBAL_ACTION_HOME
            GlobalActionType.RECENTS -> GLOBAL_ACTION_RECENTS
            GlobalActionType.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            GlobalActionType.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
        }
        return performGlobalAction(actionId)
    }
}
