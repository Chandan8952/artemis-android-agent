package com.artemis.agent.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.artemis.agent.accessibility.ArtemisAccessibilityService
import com.artemis.agent.ai.GeminiVlmClient
import com.artemis.agent.engine.ArtemisAgentEngine
import com.artemis.agent.model.AgentState
import com.artemis.agent.shizuku.ShizukuManager
import kotlinx.coroutines.*

class FloatingOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "artemis_overlay_channel"
        const val NOTIFICATION_ID = 101
        const val EXTRA_API_KEY = "extra_api_key"

        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var agentEngine: ArtemisAgentEngine? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Artemis Agent is ready"))
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apiKey = intent?.getStringExtra(EXTRA_API_KEY) ?: ""
        if (apiKey.isNotBlank()) {
            val client = GeminiVlmClient(apiKey)
            agentEngine = ArtemisAgentEngine(applicationContext, client)
            observeAgentState()
        }

        if (floatingView == null) {
            setupFloatingView()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Artemis Agent Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Artemis Android Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingView() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        // Programmatic Dark Futuristic HUD Layout
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#E61E1E2E")) // Dark purple/black translucent
        }

        // 1. Collapsed Bubble / Handle
        val bubbleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 12, 20, 12)
            setBackgroundColor(Color.parseColor("#2E2E3E"))
        }

        val titleText = TextView(this).apply {
            text = "⚡ Artemis"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val badgeText = TextView(this).apply {
            val isAdv = ShizukuManager.hasShizukuPermission()
            text = if (isAdv) " [ADVANCED ADB]" else " [STANDARD]"
            setTextColor(if (isAdv) Color.parseColor("#A6E3A1") else Color.parseColor("#F9E2AF"))
            textSize = 12f
        }

        bubbleLayout.addView(titleText)
        bubbleLayout.addView(badgeText)
        rootLayout.addView(bubbleLayout)

        // 2. Expanded Terminal Panel
        val panelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
            setPadding(0, 16, 0, 0)
        }

        val promptInput = EditText(this).apply {
            hint = "E.g. Open YouTube and play a Coldplay song"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#313244"))
            setPadding(16, 16, 16, 16)
            textSize = 13f
        }

        val thoughtText = TextView(this).apply {
            text = "Status: Idle. Enter instructions to start."
            setTextColor(Color.parseColor("#CDD6F4"))
            textSize = 12f
            setPadding(8, 12, 8, 12)
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val startBtn = Button(this).apply {
            text = "Run Task"
            setBackgroundColor(Color.parseColor("#89B4FA"))
            setTextColor(Color.BLACK)
            setOnClickListener {
                val goal = promptInput.text.toString().trim()
                if (goal.isNotBlank()) {
                    layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(rootLayout, layoutParams)
                    agentEngine?.startTask(goal)
                }
            }
        }

        val stopBtn = Button(this).apply {
            text = "Stop"
            setBackgroundColor(Color.parseColor("#F38BA8"))
            setTextColor(Color.BLACK)
            setOnClickListener {
                agentEngine?.stopTask()
            }
        }

        // Allow typing when clicking EditText
        promptInput.setOnTouchListener { _, _ ->
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(rootLayout, layoutParams)
            false
        }

        buttonRow.addView(startBtn)
        buttonRow.addView(stopBtn)

        panelLayout.addView(promptInput)
        panelLayout.addView(thoughtText)
        panelLayout.addView(buttonRow)
        rootLayout.addView(panelLayout)

        // Drag-to-move touch listener on header
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubbleLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(rootLayout, layoutParams)
                    true
                }
                else -> false
            }
        }

        floatingView = rootLayout
        windowManager.addView(rootLayout, layoutParams)
    }

    private fun observeAgentState() {
        val engine = agentEngine ?: return
        serviceScope.launch {
            engine.agentState.collect { state ->
                floatingView?.let { view ->
                    val thoughtView = (view as? ViewGroup)?.findViewById<TextView>(android.R.id.text1)
                    when (state) {
                        is AgentState.Idle -> {
                            updateNotification("Artemis Agent is Idle")
                        }
                        is AgentState.Running -> {
                            val msg = "Step ${state.step}/${state.maxSteps}: ${state.thought}"
                            updateNotification(msg)
                        }
                        is AgentState.Completed -> {
                            updateNotification("Task Finished: ${state.summary}")
                        }
                        is AgentState.Error -> {
                            updateNotification("Error: ${state.message}")
                        }
                        is AgentState.Paused -> {
                            updateNotification("Paused at step ${state.step}")
                        }
                    }
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        agentEngine?.stopTask()
        floatingView?.let {
            runCatching { windowManager.removeView(it) }
        }
        floatingView = null
    }
}
