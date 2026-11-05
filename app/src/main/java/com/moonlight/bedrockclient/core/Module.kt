package com.moonlight.bedrockclient.core

/** Categories shown in the client sidebar. */
enum class Category(val title: String) {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    PLAYER("Player"),
    BASE_FINDING("Base Finding"),
    GENERAL("General");

    val isModuleCategory: Boolean
        get() = this != GENERAL
}

/**
 * A single configurable value attached to a module.
 *
 * Settings are pure data. Persistence lives in [ConfigStore] and rendering lives in
 * the UI layer, so the same module description can drive the floating GUI, the HUD
 * and the exported configuration file.
 */
sealed class Setting(val id: String, val name: String) {

    class Toggle(id: String, name: String, val default: Boolean = false) : Setting(id, name)

    class Number(
        id: String,
        name: String,
        val default: Float,
        val min: Float,
        val max: Float,
        val step: Float = 1f,
        val unit: String = ""
    ) : Setting(id, name) {

        fun snap(value: Float): Float {
            val clamped = value.coerceIn(min, max)
            if (step <= 0f) return clamped
            val steps = Math.round((clamped - min) / step)
            return (min + steps * step).coerceIn(min, max)
        }

        fun format(value: Float): String {
            val shown = if (step >= 1f) {
                value.roundToInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", value)
            }
            return if (unit.isEmpty()) shown else "$shown$unit"
        }
    }

    class Choice(
        id: String,
        name: String,
        val options: List<String>,
        val defaultIndex: Int = 0
    ) : Setting(id, name)

    class Color(id: String, name: String, val default: Int) : Setting(id, name)

    /** A row that runs an action when tapped. */
    class Action(id: String, name: String) : Setting(id, name)

    /** A read-only diagnostic row whose value is resolved when drawn. */
    class Label(id: String, name: String, val value: () -> String) : Setting(id, name)

    /** A block entry with its own enable flag and highlight color. */
    class Block(
        id: String,
        name: String,
        val defaultEnabled: Boolean,
        val defaultColor: Int
    ) : Setting(id, name)
}

private fun Float.roundToInt(): Int = Math.round(this)

data class Module(
    val id: String,
    val name: String,
    val description: String,
    val category: Category,
    val settings: List<Setting> = emptyList(),
    /**
     * True when the module can only affect Minecraft through the in-game hook.
     * The GUI still stores the toggle so a configuration can be prepared offline,
     * but it labels the module as inactive instead of pretending it is running.
     */
    val requiresHook: Boolean = true,
    val showToggle: Boolean = true
)
