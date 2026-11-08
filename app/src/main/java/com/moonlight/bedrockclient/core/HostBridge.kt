package com.moonlight.bedrockclient.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Reports the real host state instead of assuming a hook exists.
 *
 * An ordinary Android app cannot read Minecraft's world or its renderer. Anything
 * that touches the world has to run inside the Minecraft process, so this app only
 * treats those modules as live when a hook process publishes a fresh heartbeat at
 * `files/moonlight-bridge/status.json`.
 *
 * Expected heartbeat format:
 * `{"pid": 1234, "version": "2.0.0", "timestamp": 1760000000000}`
 */
object HostBridge {

    const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"
    private const val HEARTBEAT_MAX_AGE_MS = 120_000L
    private const val TAG = "MoonlightHostBridge"
    private val ROOT_PATHS = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/adb/magisk")

    data class Status(
        val minecraftInstalled: Boolean,
        val minecraftVersion: String,
        val hooked: Boolean,
        val hookPid: Int,
        val hookVersion: String,
        val hookAgeSeconds: Long,
        val rootDetected: Boolean
    )

    fun detect(context: Context): Status {
        var installed = false
        var versionName = "not installed"
        try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(MINECRAFT_PACKAGE, 0)
            installed = true
            versionName = info.versionName ?: "unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            installed = false
        } catch (error: Throwable) {
            Log.w(TAG, "Package lookup failed", error)
        }

        var hooked = false
        var pid = 0
        var hookVersion = ""
        var ageSeconds = -1L
        val statusFile = File(context.filesDir, "moonlight-bridge/status.json")
        if (statusFile.exists()) {
            try {
                val json = JSONObject(statusFile.readText())
                pid = json.optInt("pid", 0)
                hookVersion = json.optString("version", "")
                val timestamp = json.optLong("timestamp", 0L)
                val age = System.currentTimeMillis() - timestamp
                ageSeconds = age / 1000L
                hooked = timestamp > 0L && age in 0..HEARTBEAT_MAX_AGE_MS && pid > 0
            } catch (error: Throwable) {
                Log.w(TAG, "Bridge heartbeat unreadable", error)
            }
        }

        // Keep the native hook registry honest: it only reports attached when a real
        // heartbeat was observed here, never because a module toggle was flipped.
        if (hooked) {
            NativeEngine.hookHeartbeat(pid, hookVersion)
        } else {
            NativeEngine.hookDetach()
        }

        val rooted = ROOT_PATHS.any { path -> File(path).exists() }
        return Status(installed, versionName, hooked, pid, hookVersion, ageSeconds, rooted)
    }

    fun describe(status: Status): String = when {
        status.hooked -> "attached (pid ${status.hookPid})"
        status.rootDetected -> "not attached, root available"
        else -> "not attached"
    }
}

/** Last observed host state, used by read-only label rows. */
object RuntimeStatus {
    @Volatile
    var current: HostBridge.Status = HostBridge.Status(
        minecraftInstalled = false,
        minecraftVersion = "not installed",
        hooked = false,
        hookPid = 0,
        hookVersion = "",
        hookAgeSeconds = -1,
        rootDetected = false
    )

    @Volatile
    var configPath: String = ""

    @Volatile
    var enabledCount: Int = 0

    fun refresh(context: Context) {
        current = HostBridge.detect(context)
    }
}
