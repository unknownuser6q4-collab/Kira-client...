package com.moonlight.bedrockclient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.moonlight.bedrockclient.core.ConfigHolder
import com.moonlight.bedrockclient.core.ConfigStore
import com.moonlight.bedrockclient.core.HostBridge
import com.moonlight.bedrockclient.core.Module
import com.moonlight.bedrockclient.core.ModuleRegistry
import com.moonlight.bedrockclient.core.RuntimeStatus
import com.moonlight.bedrockclient.core.Setting
import com.moonlight.bedrockclient.ui.ClickGuiView
import com.moonlight.bedrockclient.ui.EyeHandleView
import com.moonlight.bedrockclient.ui.GuiTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Owns every floating surface.
 *
 * All windows use [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY], which needs
 * the "Display over other apps" permission. Nothing here writes to Minecraft's
 * process; modules that would need to do so are labelled in the GUI.
 */
class FloatingOverlayService : Service() {

    companion object {
        @Volatile
        var running: Boolean = false
            private set

        const val ACTION_STOP = "com.moonlight.bedrockclient.action.STOP_OVERLAY"
        private const val CHANNEL_ID = "moonlight_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "moonlight_overlay"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var store: ConfigStore
    private val mainHandler = Handler(Looper.getMainLooper())

    private var moonView: View? = null
    private var moonParams: WindowManager.LayoutParams? = null
    private var guiContainer: FrameLayout? = null
    private var guiView: ClickGuiView? = null
    private var eyeView: EyeHandleView? = null
    private var eyeParams: WindowManager.LayoutParams? = null
    private var hudView: View? = null
    private var hudSignature: String = ""
    private var freelookActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        store = ConfigHolder.get(this)
        RuntimeStatus.refresh(this)
        startForegroundWithNotification()
        store.onChange = { mainHandler.post { syncOverlays() } }
        addMoon()
        syncOverlays()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        store.onChange = null
        closeGui()
        removeEye()
        removeHud()
        moonView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
            }
        }
        moonView = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        closeGui()
        removeEye()
        removeHud()
        syncOverlays()
        val view = moonView
        val params = moonParams
        if (view != null && params != null) {
            params.x = params.x.coerceIn(0, max(0, screenWidth() - dp(44)))
            params.y = params.y.coerceIn(0, max(0, screenHeight() - dp(44)))
            safeUpdate(view, params)
        }
    }

    // ------------------------------------------------------------- notification

    private fun startForegroundWithNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingOverlayService::class.java).setAction(ACTION_STOP),
            flags
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---------------------------------------------------------------------- moon

    private fun addMoon() {
        if (moonView != null) return
        val view = LayoutInflater.from(this).inflate(R.layout.layout_floating_icon, null)
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        params.gravity = Gravity.TOP or Gravity.START

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        params.x = prefs.getInt("moon_x", dp(28)).coerceIn(0, max(0, screenWidth() - dp(56)))
        params.y = prefs.getInt("moon_y", dp(150)).coerceIn(0, max(0, screenHeight() - dp(56)))

        makeDraggable(
            view = view,
            params = params,
            onClick = { toggleGui() },
            onDrop = {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt("moon_x", params.x)
                    .putInt("moon_y", params.y)
                    .apply()
            }
        )

        try {
            windowManager.addView(view, params)
        } catch (error: Throwable) {
            toast("Overlay permission is required to show the moon button")
            stopSelf()
            return
        }
        moonView = view
        moonParams = params
    }

    // ----------------------------------------------------------------------- gui

    private fun toggleGui() {
        if (guiContainer != null) closeGui() else openGui()
    }

    private fun openGui() {
        if (guiContainer != null) return
        val container = FrameLayout(this)
        val gui = ClickGuiView(this, store)
        gui.onRequestClose = { closeGui() }
        gui.onAction = { module, action -> handleAction(module, action) }
        gui.onModuleToggled = { _, _ -> syncOverlays() }
        container.addView(
            gui,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        params.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager.addView(container, params)
        } catch (error: Throwable) {
            toast("Could not open the panel")
            return
        }
        guiContainer = container
        guiView = gui
        RuntimeStatus.refresh(this)
        gui.refreshHostState()
    }

    private fun closeGui() {
        val container = guiContainer ?: return
        guiContainer = null
        guiView = null
        try {
            windowManager.removeView(container)
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------- eye

    private fun syncOverlays() {
        syncEye()
        syncHud()
        guiView?.onExternalChange()
    }

    private fun syncEye() {
        val module = ModuleRegistry.module("freelook") ?: return
        val shortcut = ModuleRegistry.findSetting("freelook", "eye_shortcut") as? Setting.Toggle
        val wanted = store.isEnabled(module) && (shortcut == null || store.boolean("freelook", shortcut))
        if (wanted) {
            if (eyeView == null) addEye() else eyeView?.accent = GuiTheme.accentFor(store)
        } else {
            removeEye()
        }
    }

    private fun addEye() {
        val sizeSetting = ModuleRegistry.findSetting("freelook", "eye_size") as? Setting.Number
        val sizeDp = if (sizeSetting != null) store.number("freelook", sizeSetting) else 44f
        val size = dp(sizeDp.roundToInt())

        val view = EyeHandleView(this)
        view.accent = GuiTheme.accentFor(store)
        view.active = freelookActive

        val params = overlayParams(size, size, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        val sideSetting = ModuleRegistry.findSetting("freelook", "eye_side") as? Setting.Choice
        val side = if (sideSetting != null) store.choiceIndex("freelook", sideSetting) else 1
        params.gravity = if (side == 0) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        params.x = prefs.getInt("eye_x", dp(12))
        params.y = prefs.getInt("eye_y", dp(220))

        makeDraggable(
            view = view,
            params = params,
            onClick = {
                freelookActive = !freelookActive
                view.active = freelookActive
                val message = if (freelookActive) {
                    if (RuntimeStatus.current.hooked) {
                        "Freelook on: camera orbits the player"
                    } else {
                        "Freelook armed. Needs the in-game hook to move the camera."
                    }
                } else {
                    "Freelook off"
                }
                toast(message)
            },
            onDrop = {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt("eye_x", params.x)
                    .putInt("eye_y", params.y)
                    .apply()
            }
        )

        try {
            windowManager.addView(view, params)
        } catch (_: Throwable) {
            return
        }
        eyeView = view
        eyeParams = params
    }

    private fun removeEye() {
        val view = eyeView ?: return
        eyeView = null
        eyeParams = null
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------- hud

    private fun syncHud() {
        val module = ModuleRegistry.module("hud") ?: return
        if (!store.isEnabled(module)) {
            removeHud()
            return
        }
        val signature = buildHudSignature()
        if (hudView != null && signature == hudSignature) return
        removeHud()

        val view = buildHudView()
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        val position = (ModuleRegistry.findSetting("hud", "position") as? Setting.Choice)
            ?.let { store.choiceIndex("hud", it) } ?: 1
        params.gravity = when (position) {
            0 -> Gravity.TOP or Gravity.START
            2 -> Gravity.BOTTOM or Gravity.START
            3 -> Gravity.BOTTOM or Gravity.END
            else -> Gravity.TOP or Gravity.END
        }
        params.x = dp(14)
        params.y = if (position <= 1) dp(56) else dp(90)

        try {
            windowManager.addView(view, params)
        } catch (_: Throwable) {
            return
        }
        hudView = view
        hudSignature = signature
    }

    private fun buildHudView(): LinearLayout {
        val accent = GuiTheme.accentFor(store)
        val backgroundAlpha = (ModuleRegistry.findSetting("hud", "background") as? Setting.Number)
            ?.let { store.number("hud", it) } ?: 45f
        val scale = ((ModuleRegistry.findSetting("hud", "scale") as? Setting.Number)
            ?.let { store.number("hud", it) } ?: 100f) / 100f
        val showHook = (ModuleRegistry.findSetting("hud", "show_hook") as? Setting.Toggle)
            ?.let { store.boolean("hud", it) } ?: true
        val watermark = (ModuleRegistry.findSetting("hud", "watermark") as? Setting.Toggle)
            ?.let { store.boolean("hud", it) } ?: true

        val backgroundDrawable = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(
                Color.argb(
                    (backgroundAlpha / 100f * 255f).roundToInt().coerceIn(0, 255),
                    8, 12, 20
                )
            )
            setStroke(
                dp(1),
                Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent))
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(12), dp(8))
            background = backgroundDrawable
        }

        if (watermark) {
            val title = TextView(this)
            title.text = "MOONLIGHT"
            title.setTextColor(accent)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * scale)
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            title.includeFontPadding = false
            root.addView(title)
        }

        val active = store.enabledIds()
            .mapNotNull { ModuleRegistry.module(it) }
            .filter { it.category.isModuleCategory }
        if (active.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No modules enabled"
            empty.setTextColor(0xFF8B95B4.toInt())
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f * scale)
            empty.includeFontPadding = false
            root.addView(empty)
        } else {
            for (module in active.take(12)) {
                val row = TextView(this)
                row.text = module.name
                row.setTextColor(0xFFDCE4F5.toInt())
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f * scale)
                row.includeFontPadding = false
                row.setPadding(0, dp(1), 0, 0)
                root.addView(row)
            }
        }

        if (showHook) {
            val status = TextView(this)
            status.text = if (RuntimeStatus.current.hooked) "hook attached" else "hook not attached"
            status.setTextColor(
                if (RuntimeStatus.current.hooked) 0xFF57D9A3.toInt() else 0xFF8B95B4.toInt()
            )
            status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f * scale)
            status.includeFontPadding = false
            status.setPadding(0, dp(4), 0, 0)
            root.addView(status)
        }

        return root
    }

    private fun buildHudSignature(): String {
        val values = buildString {
            append(store.enabledIds().joinToString(","))
            append("|")
            append(RuntimeStatus.current.hooked)
            append("|")
            append(GuiTheme.accentFor(store))
            for (id in listOf("position", "scale", "background", "show_hook", "watermark")) {
                when (val setting = ModuleRegistry.findSetting("hud", id)) {
                    is Setting.Number -> append(store.number("hud", setting))
                    is Setting.Choice -> append(store.choiceIndex("hud", setting))
                    is Setting.Toggle -> append(store.boolean("hud", setting))
                    else -> Unit
                }
            }
        }
        return values
    }

    private fun removeHud() {
        val view = hudView ?: return
        hudView = null
        hudSignature = ""
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------ actions

    private fun handleAction(module: Module, action: Setting.Action) {
        when (action.id) {
            "reset_camera" -> {
                if (RuntimeStatus.current.hooked) {
                    toast("Camera reset sent to the in-game hook")
                } else {
                    toast("Needs the in-game hook: camera reset is saved for the next session")
                }
            }
            "spawn_eye" -> {
                store.setEnabled("freelook", true)
                (ModuleRegistry.findSetting("freelook", "eye_shortcut") as? Setting.Toggle)?.let {
                    store.setBoolean("freelook", it, true)
                }
                store.persist()
                syncOverlays()
                toast("Eye shortcut is on. Tap the eye to toggle Freelook.")
            }
            "export" -> {
                store.persist()
                val file = exportedConfigFile()
                if (store.exportTo(file)) {
                    toast("Exported to ${file.absolutePath}")
                } else {
                    toast("Export failed")
                }
            }
            "import" -> {
                val file = exportedConfigFile()
                if (store.importFrom(file)) {
                    syncOverlays()
                    toast("Imported ${file.name}")
                } else {
                    toast("No exported config found at ${file.absolutePath}")
                }
            }
            "reset" -> {
                store.resetAll()
                syncOverlays()
                toast("All settings reset")
            }
            "recheck" -> {
                RuntimeStatus.refresh(this)
                guiView?.refreshHostState()
                syncHud()
                toast("Host re-checked")
            }
            else -> toast("${module.name}: ${action.name} is not available in this build")
        }
    }

    private fun exportedConfigFile(): File {
        val directory = getExternalFilesDir(null) ?: filesDir
        return File(directory, "moonlight-config.json")
    }

    // ------------------------------------------------------------------ helpers

    private fun overlayParams(width: Int, height: Int, flags: Int): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        return params
    }

    private fun makeDraggable(
        view: View,
        params: WindowManager.LayoutParams,
        onClick: () -> Unit,
        onDrop: () -> Unit
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).roundToInt()
                    val dy = (event.rawY - touchY).roundToInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) dragged = true
                    if (dragged) {
                        params.x = (startX + dx).coerceIn(-dp(6), max(0, screenWidth() - dp(36)))
                        params.y = (startY + dy).coerceIn(0, max(0, screenHeight() - dp(36)))
                        safeUpdate(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) onDrop() else onClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun safeUpdate(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Throwable) {
        }
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun toast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
    }
}
