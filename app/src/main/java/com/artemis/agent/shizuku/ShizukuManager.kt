package com.artemis.agent.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages Shizuku connection for Advanced Mode (On-Device ADB shell execution without PC).
 */
object ShizukuManager {

    private const val REQUEST_CODE_SHIZUKU = 1001

    fun isShizukuInstalledAndRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        if (!isShizukuInstalledAndRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
        if (!isShizukuInstalledAndRunning()) {
            onResult(false)
            return
        }

        if (hasShizukuPermission()) {
            onResult(true)
            return
        }

        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == REQUEST_CODE_SHIZUKU) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        try {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
        } catch (e: Throwable) {
            Shizuku.removeRequestPermissionResultListener(listener)
            onResult(false)
        }
    }

    /**
     * Executes arbitrary ADB shell command with Shizuku privileges.
     */
    suspend fun executeShell(command: String): Result<String> = withContext(Dispatchers.IO) {
        if (!hasShizukuPermission()) {
            return@withContext Result.failure(IllegalStateException("Shizuku permission not granted."))
        }

        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            Result.success(output.toString().trim())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Advanced Mode: Force stops an application.
     */
    suspend fun forceStopApp(packageName: String): Boolean {
        val result = executeShell("am force-stop $packageName")
        return result.isSuccess
    }

    /**
     * Advanced Mode: Installs an APK silently using package manager.
     */
    suspend fun installApk(apkPath: String): Boolean {
        val result = executeShell("pm install -r \"$apkPath\"")
        return result.getOrNull()?.contains("Success", ignoreCase = true) == true
    }

    /**
     * Advanced Mode: Dumps recent logcat lines for testing/diagnostic reports.
     */
    suspend fun readLogcat(filter: String = "", lines: Int = 50): String {
        val cmd = if (filter.isBlank()) {
            "logcat -d -t $lines"
        } else {
            "logcat -d -t $lines -s $filter"
        }
        return executeShell(cmd).getOrDefault("Failed to retrieve logcat output.")
    }
}
