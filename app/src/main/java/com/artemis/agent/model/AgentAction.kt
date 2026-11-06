package com.artemis.agent.model

/**
 * All actions that the Artemis Android Agent can execute.
 * Includes both standard UI gestures and Advanced Shizuku ADB commands.
 */
sealed class AgentAction {
    // 1. Standard UI Gestures (Available in All Modes)
    data class Tap(val x: Float, val y: Float, val note: String = "") : AgentAction()
    data class ClickNode(val nodeIndex: Int, val note: String = "") : AgentAction()
    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val durationMs: Long = 300L
    ) : AgentAction()
    data class Scroll(val direction: ScrollDirection) : AgentAction()
    data class TypeText(val text: String, val pressEnter: Boolean = true) : AgentAction()
    data class Global(val type: GlobalActionType) : AgentAction()
    data class LaunchApp(val packageName: String) : AgentAction()
    data class OpenUrl(val url: String) : AgentAction()
    data class Wait(val seconds: Int) : AgentAction()
    data class Finish(val summary: String, val success: Boolean = true) : AgentAction()

    // 2. Advanced Mode Commands (Requires Shizuku)
    data class AdbShell(val command: String) : AgentAction()
    data class ForceStop(val packageName: String) : AgentAction()
    data class InstallApk(val apkPath: String) : AgentAction()
    data class ReadLogcat(val filter: String = "", val lines: Int = 50) : AgentAction()
}

enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}

enum class GlobalActionType {
    BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS
}
