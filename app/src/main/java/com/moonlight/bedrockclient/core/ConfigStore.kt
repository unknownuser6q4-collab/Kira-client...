package com.moonlight.bedrockclient.core

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Single source of truth for module toggles and setting values.
 *
 * Values are kept in memory and written to `files/moonlight/config.json` on
 * [persist]. Every write is followed by [onChange] so overlays can resync.
 */
class ConfigStore(context: Context) {

    private val configFile = File(context.applicationContext.filesDir, "moonlight/config.json")
    private val values = HashMap<String, String>()
    private val enabled = HashSet<String>()

    /** Invoked after a successful persist so the overlay service can resync. */
    var onChange: (() -> Unit)? = null

    val path: String
        get() = configFile.absolutePath

    fun load() {
        values.clear()
        enabled.clear()
        if (!configFile.exists()) {
            syncNative()
            return
        }
        try {
            val root = JSONObject(configFile.readText())
            root.optJSONArray("enabled")?.let { array ->
                for (index in 0 until array.length()) {
                    val id = array.optString(index)
                    if (id.isNotEmpty()) enabled.add(id)
                }
            }
            root.optJSONObject("values")?.let { objectValue ->
                val keys = objectValue.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    values[key] = objectValue.optString(key)
                }
            }
            RuntimeStatus.enabledCount = enabled.size
            Log.i(TAG, "Loaded ${enabled.size} modules and ${values.size} values")
        } catch (error: Throwable) {
            Log.w(TAG, "Config unreadable, falling back to defaults", error)
            backupCorruptFile()
            values.clear()
            enabled.clear()
        }
        syncNative()
    }

    /**
     * Pushes the current module state into the native mirror and starts its scheduler.
     *
     * No-op when the arm64 library is missing, so the app still works on builds
     * produced without the NDK.
     */
    private fun syncNative() {
        if (!NativeEngine.available) return
        NativeEngine.start()
        NativeEngine.syncAll(ModuleRegistry.modules.map { it.id }, enabled)
    }

    private fun backupCorruptFile() {
        try {
            val parent = configFile.parentFile ?: return
            configFile.copyTo(File(parent, "config.corrupt.json"), overwrite = true)
        } catch (error: Throwable) {
            Log.w(TAG, "Could not back up corrupt config", error)
        }
    }

    fun persist() {
        try {
            configFile.parentFile?.mkdirs()
            val root = JSONObject()
            root.put("version", CONFIG_VERSION)
            root.put("enabled", JSONArray(enabled.sorted()))
            val encoded = JSONObject()
            for ((key, value) in values) encoded.put(key, value)
            root.put("values", encoded)

            val temp = File(configFile.parentFile, "config.json.tmp")
            temp.writeText(root.toString(2))
            if (configFile.exists() && !configFile.delete()) {
                Log.w(TAG, "Could not replace old config file")
            }
            if (!temp.renameTo(configFile)) {
                configFile.writeText(root.toString(2))
                temp.delete()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to save config", error)
        }
        RuntimeStatus.enabledCount = enabled.size
        RuntimeStatus.configPath = path
        onChange?.invoke()
    }

    private fun key(moduleId: String, settingId: String) = "$moduleId.$settingId"

    fun isEnabled(module: Module): Boolean = enabled.contains(module.id)

    fun isEnabled(moduleId: String): Boolean = enabled.contains(moduleId)

    fun setEnabled(moduleId: String, value: Boolean) {
        if (value) enabled.add(moduleId) else enabled.remove(moduleId)
        NativeEngine.setEnabled(moduleId, value)
    }

    fun enabledIds(): List<String> = enabled.sorted()

    fun boolean(moduleId: String, setting: Setting.Toggle): Boolean =
        values[key(moduleId, setting.id)]?.toBooleanStrictOrNull() ?: setting.default

    fun setBoolean(moduleId: String, setting: Setting.Toggle, value: Boolean) {
        values[key(moduleId, setting.id)] = value.toString()
    }

    fun number(moduleId: String, setting: Setting.Number): Float =
        setting.snap(values[key(moduleId, setting.id)]?.toFloatOrNull() ?: setting.default)

    fun setNumber(moduleId: String, setting: Setting.Number, value: Float) {
        values[key(moduleId, setting.id)] = setting.snap(value).toString()
    }

    fun choiceIndex(moduleId: String, setting: Setting.Choice): Int {
        val stored = values[key(moduleId, setting.id)]?.toIntOrNull() ?: setting.defaultIndex
        if (setting.options.isEmpty()) return 0
        return stored.coerceIn(0, setting.options.size - 1)
    }

    fun setChoiceIndex(moduleId: String, setting: Setting.Choice, index: Int) {
        if (setting.options.isEmpty()) return
        values[key(moduleId, setting.id)] = index.coerceIn(0, setting.options.size - 1).toString()
    }

    fun color(moduleId: String, setting: Setting.Color): Int =
        values[key(moduleId, setting.id)]?.toIntOrNull() ?: setting.default

    fun setColor(moduleId: String, setting: Setting.Color, color: Int) {
        values[key(moduleId, setting.id)] = color.toString()
    }

    class BlockValue(val enabled: Boolean, val color: Int)

    fun block(moduleId: String, setting: Setting.Block): BlockValue {
        val raw = values[key(moduleId, setting.id)] ?: return BlockValue(setting.defaultEnabled, setting.defaultColor)
        val parts = raw.split('|')
        val on = parts.getOrNull(0)?.toBooleanStrictOrNull() ?: setting.defaultEnabled
        val color = parts.getOrNull(1)?.toIntOrNull() ?: setting.defaultColor
        return BlockValue(on, color)
    }

    fun setBlock(moduleId: String, setting: Setting.Block, enabled: Boolean, color: Int) {
        values[key(moduleId, setting.id)] = if (enabled) "true|$color" else "false|$color"
    }

    fun resetAll() {
        values.clear()
        enabled.clear()
        persist()
        syncNative()
    }

    /** Replaces the in-memory configuration with the contents of an exported file. */
    fun importFrom(source: File): Boolean {
        if (!source.exists()) return false
        return try {
        val root = JSONObject(source.readText())
        val nextValues = HashMap<String, String>()
        val nextEnabled = HashSet<String>()
        root.optJSONArray("enabled")?.let { array ->
            for (index in 0 until array.length()) {
                val id = array.optString(index)
                if (id.isNotEmpty()) nextEnabled.add(id)
            }
        }
        root.optJSONObject("values")?.let { objectValue ->
            val keys = objectValue.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                nextValues[key] = objectValue.optString(key)
            }
        }
        values.clear()
        values.putAll(nextValues)
        enabled.clear()
        enabled.addAll(nextEnabled)
            persist()
            syncNative()
            true
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to import config", error)
            false
        }
    }


    fun exportTo(destination: File): Boolean = try {
        destination.parentFile?.mkdirs()
        destination.writeText(configFile.takeIf { it.exists() }?.readText() ?: buildEmptyJson())
        true
    } catch (error: Throwable) {
        Log.e(TAG, "Failed to export config", error)
        false
    }

    private fun buildEmptyJson(): String = JSONObject()
        .put("version", CONFIG_VERSION)
        .put("enabled", JSONArray())
        .put("values", JSONObject())
        .toString(2)

    private companion object {
        const val TAG = "MoonlightConfig"
        const val CONFIG_VERSION = 2
    }
}

/** Lazily creates one process-wide store so activities and the service agree. */
object ConfigHolder {
    @Volatile
    private var instance: ConfigStore? = null

    fun get(context: Context): ConfigStore {
        val existing = instance
        if (existing != null) return existing
        return synchronized(this) {
            val created = instance ?: ConfigStore(context).also {
                it.load()
                it.path.let { path -> RuntimeStatus.configPath = path }
            }
            instance = created
            created
        }
    }
}
