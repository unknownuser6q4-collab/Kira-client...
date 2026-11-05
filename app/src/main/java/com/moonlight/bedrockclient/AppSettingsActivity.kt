package com.moonlight.bedrockclient

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.moonlight.bedrockclient.core.ConfigHolder
import com.moonlight.bedrockclient.core.ConfigStore
import com.moonlight.bedrockclient.core.ModuleRegistry
import com.moonlight.bedrockclient.core.Setting
import com.moonlight.bedrockclient.ui.GuiTheme
import kotlin.math.roundToInt

class AppSettingsActivity : AppCompatActivity() {

    private lateinit var store: ConfigStore

    private lateinit var accentRow: LinearLayout
    private lateinit var seekScale: SeekBar
    private lateinit var seekDim: SeekBar
    private lateinit var tvScaleValue: TextView
    private lateinit var tvDimValue: TextView
    private lateinit var checkHaptics: CheckBox
    private lateinit var checkDescriptions: CheckBox
    private lateinit var tvConfigPath: TextView

    private val scaleSetting: Setting.Number?
        get() = ModuleRegistry.findSetting("app_settings", "gui_scale") as? Setting.Number
    private val dimSetting: Setting.Number?
        get() = ModuleRegistry.findSetting("app_settings", "overlay_dim") as? Setting.Number
    private val hapticsSetting: Setting.Toggle?
        get() = ModuleRegistry.findSetting("app_settings", "haptics") as? Setting.Toggle
    private val descriptionsSetting: Setting.Toggle?
        get() = ModuleRegistry.findSetting("app_settings", "descriptions") as? Setting.Toggle
    private val accentSetting: Setting.Choice?
        get() = ModuleRegistry.findSetting("app_theme", "accent") as? Setting.Choice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)
        store = ConfigHolder.get(this)

        accentRow = findViewById(R.id.accentRow)
        seekScale = findViewById(R.id.seekScale)
        seekDim = findViewById(R.id.seekDim)
        tvScaleValue = findViewById(R.id.tvScaleValue)
        tvDimValue = findViewById(R.id.tvDimValue)
        checkHaptics = findViewById(R.id.checkHaptics)
        checkDescriptions = findViewById(R.id.checkDescriptions)
        tvConfigPath = findViewById(R.id.tvConfigPath)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnResetConfig).setOnClickListener {
            store.resetAll()
            bindValues()
            Toast.makeText(this, "All settings reset", Toast.LENGTH_SHORT).show()
        }

        seekScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 70f + progress * 5f
                tvScaleValue.text = getString(R.string.percent_value, value.roundToInt())
                if (fromUser) {
                    scaleSetting?.let { store.setNumber("app_settings", it, value) }
                    store.persist()
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        seekDim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress * 5f
                tvDimValue.text = getString(R.string.percent_value, value.roundToInt())
                if (fromUser) {
                    dimSetting?.let { store.setNumber("app_settings", it, value) }
                    store.persist()
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        checkHaptics.setOnCheckedChangeListener { _, checked ->
            hapticsSetting?.let { store.setBoolean("app_settings", it, checked) }
            store.persist()
        }

        checkDescriptions.setOnCheckedChangeListener { _, checked ->
            descriptionsSetting?.let { store.setBoolean("app_settings", it, checked) }
            store.persist()
        }

        bindValues()
    }

    private fun bindValues() {
        val scale = scaleSetting?.let { store.number("app_settings", it) } ?: 100f
        val dim = dimSetting?.let { store.number("app_settings", it) } ?: 55f

        seekScale.progress = ((scale - 70f) / 5f).roundToInt().coerceIn(0, seekScale.max)
        seekDim.progress = (dim / 5f).roundToInt().coerceIn(0, seekDim.max)
        tvScaleValue.text = getString(R.string.percent_value, scale.roundToInt())
        tvDimValue.text = getString(R.string.percent_value, dim.roundToInt())

        checkHaptics.isChecked = hapticsSetting?.let { store.boolean("app_settings", it) } ?: true
        checkDescriptions.isChecked =
            descriptionsSetting?.let { store.boolean("app_settings", it) } ?: true

        tvConfigPath.text = "Config file: ${store.path}"
        buildAccentRow()
    }

    private fun buildAccentRow() {
        accentRow.removeAllViews()
        val current = accentSetting?.let { store.choiceIndex("app_theme", it) } ?: 0
        val size = dp(34)
        GuiTheme.accentPresets.forEachIndexed { index, preset ->
            val swatch = View(this)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = dp(10)
            swatch.layoutParams = params
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(preset.second)
                if (index == current) setStroke(dp(2), Color.WHITE)
            }
            swatch.setOnClickListener {
                accentSetting?.let { setting -> store.setChoiceIndex("app_theme", setting, index) }
                store.persist()
                buildAccentRow()
            }
            accentRow.addView(swatch)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
