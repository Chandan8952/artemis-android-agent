package com.artemis.agent.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.artemis.agent.accessibility.ArtemisAccessibilityService
import com.artemis.agent.screen.ScreenCaptureManager
import com.artemis.agent.service.FloatingOverlayService
import com.artemis.agent.shizuku.ShizukuManager
import com.artemis.agent.ui.theme.ArtemisTheme

class MainActivity : ComponentActivity() {

    private val PREFS_NAME = "artemis_prefs"
    private val KEY_GEMINI_API = "gemini_api_key"

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var hasScreenCapturePermission by mutableStateOf(false)
    private var hasShizukuPermission by mutableStateOf(false)
    private var isShizukuInstalled by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
    private var apiKey by mutableStateOf("")

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureManager.initProjection(this, result.resultCode, result.data!!)
            hasScreenCapturePermission = true
            Toast.makeText(this, "Screen capture initialized", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission declined", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKey = prefs.getString(KEY_GEMINI_API, "") ?: ""

        setContent {
            ArtemisTheme {
                MainScreen(
                    hasOverlayPermission = hasOverlayPermission,
                    hasAccessibilityPermission = hasAccessibilityPermission,
                    hasScreenCapturePermission = hasScreenCapturePermission,
                    hasShizukuPermission = hasShizukuPermission,
                    isShizukuInstalled = isShizukuInstalled,
                    apiKey = apiKey,
                    isServiceRunning = isServiceRunning,
                    onSaveApiKey = { newKey ->
                        prefs.edit().putString(KEY_GEMINI_API, newKey).apply()
                        apiKey = newKey
                        Toast.makeText(this, "API Key saved successfully", Toast.LENGTH_SHORT).show()
                    },
                    onRequestOverlay = { requestOverlayPermission() },
                    onRequestAccessibility = { requestAccessibilityPermission() },
                    onRequestScreenCapture = { requestScreenCapture() },
                    onRequestShizuku = { requestShizuku() },
                    onToggleService = { toggleFloatingService() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        hasAccessibilityPermission = ArtemisAccessibilityService.isRunning
        isShizukuInstalled = ShizukuManager.isShizukuInstalledAndRunning()
        hasShizukuPermission = ShizukuManager.hasShizukuPermission()
        isServiceRunning = FloatingOverlayService.isRunning
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Find 'Artemis Agent' and switch it ON", Toast.LENGTH_LONG).show()
    }

    private fun requestScreenCapture() {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    private fun requestShizuku() {
        if (!isShizukuInstalled) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
            startActivity(browserIntent)
            Toast.makeText(this, "Please install Shizuku to unlock Advanced ADB mode", Toast.LENGTH_LONG).show()
            return
        }

        ShizukuManager.requestShizukuPermission { granted ->
            runOnUiThread {
                hasShizukuPermission = granted
                val msg = if (granted) "Shizuku Advanced Mode Enabled!" else "Shizuku permission denied"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFloatingService() {
        val serviceIntent = Intent(this, FloatingOverlayService::class.java).apply {
            putExtra(FloatingOverlayService.EXTRA_API_KEY, apiKey)
        }

        if (FloatingOverlayService.isRunning) {
            stopService(serviceIntent)
            isServiceRunning = false
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            isServiceRunning = true
            Toast.makeText(this, "Artemis Floating Agent Launched!", Toast.LENGTH_SHORT).show()
        }
    }
}
