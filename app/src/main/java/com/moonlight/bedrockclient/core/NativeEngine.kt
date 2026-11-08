package com.moonlight.bedrockclient.core

import android.util.Log

/**
 * Kotlin front door for `libmoonlight_engine.so` (arm64-v8a only).
 *
 * The native layer mirrors the module catalogue in C++ and runs a 100 Hz scheduler
 * inside this app's own process. It is real work with real measurements, but it
 * cannot modify Minecraft: another app's memory is out of reach on a stock device, so
 * [hookState] reports DETACHED until a bridge living inside the Minecraft process
 * publishes a heartbeat. Nothing here pretends otherwise.
 *
 * Every entry point degrades to a no-op when the library is absent (32-bit devices,
 * or a build produced without the NDK), so callers never branch on ABI.
 */
object NativeEngine {

    const val HOOK_DETACHED = 0
    const val HOOK_ATTACHED = 1
    const val HOOK_STALE = 2

    private const val TAG = "MoonlightNative"
    private const val LIBRARY = "moonlight_engine"

    /** True when the shared object loaded and JNI symbols resolved. */
    val available: Boolean = try {
        System.loadLibrary(LIBRARY)
        true
    } catch (error: Throwable) {
        Log.w(TAG, "Native engine unavailable on this build: ${error.message}")
        false
    }

    val version: String
        get() = call("version") { nativeVersion() }.orEmpty()

    val abi: String
        get() = call("abi") { nativeAbi() }.orEmpty()

    fun start(): Boolean = call("start") { nativeStart() } == true

    fun stop() {
        call("stop") { nativeStop() }
    }

    fun isRunning(): Boolean = call("isRunning") { nativeIsRunning() } == true

    fun registerModule(id: String) {
        call("registerModule") { nativeRegisterModule(id) }
    }

    /** Returns how many ids were newly registered. */
    fun registerModules(ids: Collection<String>): Int =
        call("registerModules") { nativeRegisterModules(ids.toTypedArray()) } ?: 0

    fun setEnabled(id: String, enabled: Boolean) {
        call("setEnabled") { nativeSetEnabled(id, enabled) }
    }

    fun enabledCount(): Int = call("enabledCount") { nativeEnabledCount() } ?: 0

    fun moduleCount(): Int = call("moduleCount") { nativeModuleCount() } ?: 0

    fun enabledIds(): List<String> =
        call("enabledIds") { nativeEnabledIds().toList() }.orEmpty()

    /**
     * Pushes the whole Kotlin state into the native mirror.
     *
     * Registration first so an id exists before its flag is written; every id is
     * pushed (not just the enabled ones) so a disabled module clears its native flag.
     */
    fun syncAll(moduleIds: Collection<String>, enabled: Set<String>) {
        if (!available) return
        registerModules(moduleIds)
        for (id in moduleIds) {
            setEnabled(id, enabled.contains(id))
        }
    }

    fun hookState(): Int = call("hookState") { nativeHookState() } ?: HOOK_DETACHED

    fun hookDescription(): String =
        call("hookDescribe") { nativeHookDescribe() }.orEmpty().ifEmpty { "unavailable" }

    fun hookVersion(): String = call("hookVersion") { nativeHookVersion() }.orEmpty()

    /** Called from [HostBridge] when a bridge heartbeat really exists. */
    fun hookHeartbeat(pid: Int, version: String) {
        call("hookHeartbeat") { nativeHookHeartbeat(pid, version) }
    }

    fun hookDetach() {
        call("hookDetach") { nativeHookDetach() }
    }

    data class Stats(
        val ticks: Long,
        val meanMicros: Long,
        val peakMicros: Long,
        val uptimeMs: Long,
        val overruns: Long,
        val modules: Int,
        val enabled: Int,
        val tickHz: Int
    )

    fun stats(): Stats? {
        val raw = call("stats") { nativeStats() } ?: return null
        if (raw.size < 8) return null
        return Stats(
            ticks = raw[0],
            meanMicros = raw[1],
            peakMicros = raw[2],
            uptimeMs = raw[3],
            overruns = raw[4],
            modules = raw[5].toInt(),
            enabled = raw[6].toInt(),
            tickHz = raw[7].toInt()
        )
    }

    /** One-line summary used by the diagnostics rows in the module catalogue. */
    fun describe(): String {
        if (!available) return "not loaded (arm64 build only)"
        if (!isRunning()) return "loaded, stopped"
        val current = stats() ?: return "loaded, no stats"
        val mean = if (current.meanMicros > 0) "${current.meanMicros}us" else "<1us"
        return "$abi ${current.tickHz}Hz ${current.ticks} ticks, $mean mean, " +
            "${current.enabled}/${current.modules} enabled"
    }

    private inline fun <T> call(
        label: String,
        block: () -> T?
    ): T? {
        if (!available) return null
        return try {
            block()
        } catch (error: Throwable) {
            Log.w(TAG, "native $label failed: ${error.message}")
            null
        }
    }

    private external fun nativeStart(): Boolean

    private external fun nativeStop()

    private external fun nativeIsRunning(): Boolean

    private external fun nativeVersion(): String

    private external fun nativeAbi(): String

    private external fun nativeRegisterModule(id: String): Boolean

    private external fun nativeRegisterModules(ids: Array<String>): Int

    private external fun nativeSetEnabled(id: String, enabled: Boolean): Boolean

    private external fun nativeEnabledCount(): Int

    private external fun nativeModuleCount(): Int

    private external fun nativeEnabledIds(): Array<String>

    private external fun nativeStats(): LongArray

    private external fun nativeHookState(): Int

    private external fun nativeHookDescribe(): String

    private external fun nativeHookHeartbeat(pid: Int, version: String)

    private external fun nativeHookDetach()

    private external fun nativeHookVersion(): String
}
