package com.moonlight.bedrockclient.ui

import android.graphics.Color
import com.moonlight.bedrockclient.core.ConfigStore
import com.moonlight.bedrockclient.core.ModuleRegistry
import com.moonlight.bedrockclient.core.Setting

object GuiTheme {

    /** Accent presets offered in the Theme page. */
    val accentPresets: List<Pair<String, Int>> = listOf(
        "Moonlight" to Color.rgb(138, 153, 255),
        "Kuro" to Color.rgb(163, 180, 255),
        "Spectre" to Color.rgb(92, 225, 230),
        "Emerald" to Color.rgb(84, 227, 155),
        "Crimson" to Color.rgb(255, 92, 122),
        "Amber" to Color.rgb(255, 196, 107)
    )

    /** Palette used when cycling a per-block ESP color. */
    val blockPalette: IntArray = intArrayOf(
        Color.rgb(180, 92, 255),
        Color.rgb(255, 154, 60),
        Color.rgb(255, 209, 102),
        Color.rgb(92, 225, 230),
        Color.rgb(107, 227, 138),
        Color.rgb(255, 92, 122),
        Color.rgb(155, 107, 255),
        Color.rgb(89, 200, 255),
        Color.rgb(240, 240, 255),
        Color.rgb(160, 170, 195)
    )

    fun accentAt(index: Int): Int = accentPresets.getOrElse(index) { accentPresets[0] }.second

    /** Resolves the accent currently stored in the shared configuration. */
    fun accentFor(store: ConfigStore): Int {
        val setting = ModuleRegistry.findSetting("app_theme", "accent") as? Setting.Choice
            ?: return accentAt(0)
        return accentAt(store.choiceIndex("app_theme", setting))
    }
}
