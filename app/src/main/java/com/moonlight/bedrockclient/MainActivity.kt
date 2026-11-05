package com.moonlight.bedrockclient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.moonlight.bedrockclient.core.ConfigHolder
import com.moonlight.bedrockclient.core.ConfigStore
import com.moonlight.bedrockclient.core.HostBridge
import com.moonlight.bedrockclient.core.RuntimeStatus

class MainActivity : AppCompatActivity() {

    private lateinit var store: ConfigStore
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvOverlayState: TextView
    private lateinit var tvMinecraftState: TextView
    private lateinit var tvHookState: TextView
    private lateinit var tvHookBanner: TextView
    private lateinit var dvOverlay: View
    private lateinit var dvMinecraft: View
    private lateinit var dvHook: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = ConfigHolder.get(this)
        RuntimeStatus.refresh(this)

        tvOverlayState = findViewById(R.id.tvOverlayState)
        tvMinecraftState = findViewById(R.id.tvMinecraftState)
        tvHookState = findViewById(R.id.tvHookState)
        tvHookBanner = findViewById(R.id.tvHookBanner)
        dvOverlay = findViewById(R.id.dvOverlay)
        dvMinecraft = findViewById(R.id.dvMinecraft)
        dvHook = findViewById(R.id.dvHook)

        findViewById<TextView>(R.id.btnStart).setOnClickListener { startClient() }
        findViewById<TextView>(R.id.btnStop).setOnClickListener { stopClient() }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnLaunch).setOnClickListener { launchMinecraft() }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        RuntimeStatus.refresh(this)
        refreshStatus()
    }

    private fun refreshStatus() {
        val status = RuntimeStatus.current
        val overlayGranted = Settings.canDrawOverlays(this)

        dot(dvOverlay, overlayGranted)
        tvOverlayState.text = if (overlayGranted) "granted" else "not granted"

        dot(dvMinecraft, status.minecraftInstalled)
        tvMinecraftState.text = if (status.minecraftInstalled) {
            "v${status.minecraftVersion}"
        } else {
            "not installed"
        }

        dot(dvHook, status.hooked)
        tvHookState.text = when {
            status.hooked -> "pid ${status.hookPid}"
            else -> "not attached"
        }

        tvHookBanner.text = if (status.hooked) {
            "In-game hook attached (pid ${status.hookPid}, heartbeat ${status.hookAgeSeconds}s ago). " +
                "World modules are live."
        } else {
            "No in-game hook. Every module can still be configured and exported, but ESP, " +
                "Fullbright, Freecam and base finding can only affect Minecraft from inside its " +
                "own process. Android prevents this app from reading or drawing another app's " +
                "world. See the README for the supported hook paths."
        }

        if (FloatingOverlayService.running) {
            tvOverlayState.text = "running"
            dot(dvOverlay, true)
        }
    }

    private fun dot(view: View, ok: Boolean) {
        val color = if (ok) {
            ContextCompat.getColor(this, R.color.ok)
        } else {
            ContextCompat.getColor(this, R.color.warn)
        }
        view.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun startClient() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermission()
            return
        }
        val intent = Intent(this, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Moonlight overlay started", Toast.LENGTH_SHORT).show()
        handler.postDelayed({ refreshStatus() }, 700)
    }

    private fun stopClient() {
        stopService(Intent(this, FloatingOverlayService::class.java))
        Toast.makeText(this, "Moonlight overlay stopped", Toast.LENGTH_SHORT).show()
        handler.postDelayed({ refreshStatus() }, 400)
    }

    private fun openOverlayPermission() {
        Toast.makeText(this, "Allow display over other apps to use the moon button", Toast.LENGTH_LONG).show()
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Throwable) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun launchMinecraft() {
        val intent = packageManager.getLaunchIntentForPackage(HostBridge.MINECRAFT_PACKAGE)
        if (intent == null) {
            Toast.makeText(this, "Minecraft Bedrock is not installed", Toast.LENGTH_LONG).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }
}
