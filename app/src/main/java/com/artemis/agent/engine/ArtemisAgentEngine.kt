package com.artemis.agent.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.artemis.agent.accessibility.ArtemisAccessibilityService
import com.artemis.agent.ai.GeminiVlmClient
import com.artemis.agent.model.*
import com.artemis.agent.screen.ScreenCaptureManager
import com.artemis.agent.shizuku.ShizukuManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArtemisAgentEngine(
    private val context: Context,
    private val geminiClient: GeminiVlmClient
) {
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentJob: Job? = null

    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val history = mutableListOf<String>()

    fun startTask(goal: String, maxSteps: Int = 25) {
        currentJob?.cancel()
        history.clear()

        currentJob = engineScope.launch {
            val isShizukuActive = ShizukuManager.hasShizukuPermission()
            _agentState.value = AgentState.Running(
                step = 1,
                maxSteps = maxSteps,
                goal = goal,
                thought = "Initializing Artemis Autonomous Agent...",
                lastAction = "Starting",
                isShizukuActive = isShizukuActive
            )

            var currentStep = 1
            while (isActive && currentStep <= maxSteps) {
                val accessibility = ArtemisAccessibilityService.instance
                if (accessibility == null) {
                    _agentState.value = AgentState.Error("Artemis Accessibility Service is not active. Please enable in Settings.", currentStep)
                    break
                }

                // 1. Capture Screen
                val screenshot = ScreenCaptureManager.captureScreenBitmap(context)
                val base64Screen = screenshot?.let { ScreenCaptureManager.bitmapToBase64(it) }

                // 2. Dump UI Hierarchy
                val uiNodes = accessibility.dumpHierarchy()

                // 3. Ask Gemini VLM for next action
                val (thought, action) = try {
                    geminiClient.decideNextAction(
                        goal = goal,
                        step = currentStep,
                        maxSteps = maxSteps,
                        history = history,
                        uiNodes = uiNodes,
                        screenshotBase64 = base64Screen,
                        isAdvancedMode = isShizukuActive
                    )
                } catch (e: Throwable) {
                    _agentState.value = AgentState.Error("AI Inference error: ${e.message}", currentStep)
                    break
                }

                // 4. Update state with thinking and action
                _agentState.value = AgentState.Running(
                    step = currentStep,
                    maxSteps = maxSteps,
                    goal = goal,
                    thought = thought,
                    lastAction = action::class.simpleName ?: "Action",
                    isShizukuActive = isShizukuActive
                )

                // 5. Execute Action
                val executionResult = executeAction(action, accessibility)
                history.add("Step $currentStep: $thought -> $action [Result: $executionResult]")

                // Check for completion
                if (action is AgentAction.Finish) {
                    _agentState.value = AgentState.Completed(
                        summary = action.summary,
                        totalSteps = currentStep,
                        success = action.success
                    )
                    break
                }

                currentStep++
                // Allow UI to render animations before next turn
                delay(1500)
            }

            if (currentStep > maxSteps && _agentState.value is AgentState.Running) {
                _agentState.value = AgentState.Completed(
                    summary = "Max steps limit ($maxSteps) reached.",
                    totalSteps = maxSteps,
                    success = false
                )
            }
        }
    }

    private suspend fun executeAction(
        action: AgentAction,
        accessibility: ArtemisAccessibilityService
    ): String = withContext(Dispatchers.Main) {
        when (action) {
            is AgentAction.Tap -> {
                val ok = accessibility.tapAt(action.x, action.y)
                "Tap at (${action.x}, ${action.y}): $ok"
            }
            is AgentAction.ClickNode -> {
                val ok = accessibility.clickNodeByIndex(action.nodeIndex)
                "Click node [${action.nodeIndex}]: $ok"
            }
            is AgentAction.Swipe -> {
                val ok = accessibility.swipe(action.startX, action.startY, action.endX, action.endY, action.durationMs)
                "Swipe: $ok"
            }
            is AgentAction.Scroll -> {
                val metrics = context.resources.displayMetrics
                val midX = metrics.widthPixels / 2f
                val midY = metrics.heightPixels / 2f
                val offset = 400f

                val (startX, startY, endX, endY) = when (action.direction) {
                    ScrollDirection.UP -> listOf(midX, midY - offset, midX, midY + offset)
                    ScrollDirection.DOWN -> listOf(midX, midY + offset, midX, midY - offset)
                    ScrollDirection.LEFT -> listOf(midX + offset, midY, midX - offset, midY)
                    ScrollDirection.RIGHT -> listOf(midX - offset, midY, midX + offset, midY)
                }
                val ok = accessibility.swipe(startX, startY, endX, endY, 300L)
                "Scroll ${action.direction}: $ok"
            }
            is AgentAction.TypeText -> {
                val ok = accessibility.typeText(action.text)
                "Type \"${action.text}\": $ok"
            }
            is AgentAction.Global -> {
                val ok = accessibility.performGlobal(action.type)
                "Global ${action.type}: $ok"
            }
            is AgentAction.LaunchApp -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    "Launched ${action.packageName}"
                } else {
                    "App ${action.packageName} not found"
                }
            }
            is AgentAction.OpenUrl -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened URL ${action.url}"
            }
            is AgentAction.Wait -> {
                delay(action.seconds * 1000L)
                "Waited ${action.seconds}s"
            }
            is AgentAction.AdbShell -> {
                val output = ShizukuManager.executeShell(action.command).getOrDefault("Failed to run command.")
                "ADB Shell [${action.command}]: $output"
            }
            is AgentAction.ForceStop -> {
                val ok = ShizukuManager.forceStopApp(action.packageName)
                "Force stop ${action.packageName}: $ok"
            }
            is AgentAction.InstallApk -> {
                val ok = ShizukuManager.installApk(action.apkPath)
                "Install APK: $ok"
            }
            is AgentAction.ReadLogcat -> {
                val logs = ShizukuManager.readLogcat(action.filter, action.lines)
                "Logcat: ${logs.take(200)}..."
            }
            is AgentAction.Finish -> {
                "Finished task: ${action.summary}"
            }
        }
    }

    fun stopTask() {
        currentJob?.cancel()
        _agentState.value = AgentState.Idle
    }
}
