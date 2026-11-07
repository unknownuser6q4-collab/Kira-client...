package com.moonlight.bedrockclient.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.moonlight.bedrockclient.core.Category
import com.moonlight.bedrockclient.core.ConfigStore
import com.moonlight.bedrockclient.core.Module
import com.moonlight.bedrockclient.core.ModuleRegistry
import com.moonlight.bedrockclient.core.RuntimeStatus
import com.moonlight.bedrockclient.core.Setting
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The floating client panel.
 *
 * Everything is drawn on a single canvas: there is no XML layout, no RecyclerView
 * and no IME dependency. The search field uses an on-screen keyboard so the panel
 * can live in a non-focusable overlay window above Minecraft without stealing input.
 */
@SuppressLint("ViewConstructor")
class ClickGuiView(context: Context, private val store: ConfigStore) : View(context) {

    // ------------------------------------------------------------- public hooks

    var onRequestClose: () -> Unit = {}
    var onAction: (Module, Setting.Action) -> Unit = { _, _ -> }
    var onModuleToggled: (Module, Boolean) -> Unit = { _, _ -> }

    fun onExternalChange() {
        computeMetrics()
        rebuildLayout()
        invalidate()
    }

    fun refreshHostState() {
        invalidate()
    }

    fun accentColor(): Int = accent

    // ------------------------------------------------------------------ paints

    private val density = resources.displayMetrics.density
    // displayMetrics.scaledDensity is deprecated; it is just density * fontScale.
    private val scaledDensity = density * resources.configuration.fontScale

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    private var accent: Int = GuiTheme.accentAt(0)

    private val windowColor = 0xF20A0E18.toInt()
    private val sidebarColor = 0xFF080B13.toInt()
    private val cardColor = 0xFF111726.toInt()
    private val cardStrokeColor = 0xFF1D2740.toInt()
    private val textPrimary = 0xFFF2F5FF.toInt()
    private val textSecondary = 0xFF9AA5C4.toInt()
    private val textMuted = 0xFF5F6A88.toInt()
    private val warnColor = 0xFFFFC46B.toInt()
    private val chipColor = 0xFF161E30.toInt()
    private val dividerColor = 0xFF18202F.toInt()

    // ------------------------------------------------------------------ layout

    private val windowRect = RectF()
    private val sidebarRect = RectF()
    private val listRect = RectF()
    private val searchRect = RectF()
    private val allChipRect = RectF()
    private val enabledChipRect = RectF()
    private val closeRect = RectF()
    private val scrollTrack = RectF()
    private val scrollThumb = RectF()
    private val keyboardRect = RectF()
    private val hueRect = RectF()
    private val sliderRect = RectF()

    private val categoryRects = HashMap<Category, RectF>()
    private val generalRects = HashMap<String, RectF>()

    private var contentLeft = 0f
    private var contentRight = 0f

    private class Card(
        val module: Module,
        val top: Float,
        val height: Float,
        val expanded: Boolean,
        val settingRects: List<RectF>
    )

    private class Key(val label: String, val rect: RectF)

    private sealed class Hit {
        object Close : Hit()
        object Search : Hit()
        object ChipAll : Hit()
        object ChipEnabled : Hit()
        object Scrollbar : Hit()
        object Outside : Hit()
        class CategoryRow(val category: Category) : Hit()
        class GeneralRow(val module: Module) : Hit()
        class CardBody(val module: Module) : Hit()
        class ModuleSwitch(val module: Module) : Hit()
        class SettingRow(val module: Module, val setting: Setting, val control: Boolean) : Hit()
        class KeyPress(val label: String) : Hit()
    }

    private val cards = ArrayList<Card>()
    private val keys = ArrayList<Key>()

    private var selectedCategory = Category.RENDER
    private var searchQuery = ""
    private var enabledOnly = false
    private var keyboardOpen = false
    private val expandedIds = HashSet<String>()

    private var scrollY = 0f
    private var maxScroll = 0f
    private var contentHeight = 0f
    private var listHeight = 0f

    private var activeColorKey: String? = null

    // ------------------------------------------------------------------- touch

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var lastTapX = 0f
    private var moved = false
    private var dragMode = 0 // 0 none, 1 list, 2 scrollbar, 3 slider, 4 hue
    private var sliderModule: Module? = null
    private var sliderSetting: Setting.Number? = null
    private var hueModule: Module? = null
    private var hueSetting: Setting.Color? = null

    init {
        isClickable = true
        isFocusable = false
        refreshAccent()
    }

    // ------------------------------------------------------------- scale helpers

    private fun scaleFactor(): Float {
        val setting = ModuleRegistry.findSetting("app_settings", "gui_scale") as? Setting.Number
            ?: return 1f
        return (store.number("app_settings", setting) / 100f).coerceIn(0.7f, 1.6f)
    }

    private fun dp(value: Float): Float = value * density * scaleFactor()

    private fun sp(value: Float): Float = value * scaledDensity * scaleFactor()

    private fun compactCards(): Boolean {
        val setting = ModuleRegistry.findSetting("app_theme", "compact") as? Setting.Toggle
            ?: return false
        return store.boolean("app_theme", setting)
    }

    private fun showDescriptions(): Boolean {
        val setting = ModuleRegistry.findSetting("app_settings", "descriptions") as? Setting.Toggle
            ?: return true
        return store.boolean("app_settings", setting)
    }

    private fun cardHeaderHeight(): Float = if (compactCards()) dp(46f) else dp(58f)

    private fun closeOnOutside(): Boolean {
        val setting = ModuleRegistry.findSetting("app_touch", "close_outside") as? Setting.Toggle
            ?: return true
        return store.boolean("app_touch", setting)
    }

    private fun refreshAccent() {
        val setting = ModuleRegistry.findSetting("app_theme", "accent") as? Setting.Choice
        accent = if (setting == null) GuiTheme.accentAt(0) else GuiTheme.accentAt(store.choiceIndex("app_theme", setting))
    }

    private fun haptic() {
        try {
            val setting = ModuleRegistry.findSetting("app_settings", "haptics") as? Setting.Toggle
            if (setting != null && store.boolean("app_settings", setting)) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        } catch (_: Throwable) {
            // Haptics are best effort on overlay windows.
        }
    }

    // -------------------------------------------------------------- measurement

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeMetrics()
        rebuildLayout()
    }

    private fun computeMetrics() {
        if (width == 0 || height == 0) return
        val margin = dp(10f)
        val w = min(width - margin * 2f, dp(900f)).coerceAtLeast(dp(300f))
        val h = min(height - margin * 2f, dp(640f)).coerceAtLeast(dp(240f))
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        windowRect.set(left, top, left + w, top + h)

        val sidebarWidth = dp(168f).coerceAtMost(w * 0.36f)
        sidebarRect.set(left, top, left + sidebarWidth, top + h)

        contentLeft = left + sidebarWidth + dp(14f)
        contentRight = left + w - dp(14f)

        closeRect.set(contentRight - dp(28f), top + dp(12f), contentRight, top + dp(40f))
        searchRect.set(contentLeft, top + dp(44f), contentRight, top + dp(82f))

        val chipTop = searchRect.bottom + dp(8f)
        allChipRect.set(contentLeft, chipTop, contentLeft + dp(54f), chipTop + dp(26f))
        enabledChipRect.set(allChipRect.right + dp(8f), chipTop, allChipRect.right + dp(8f) + dp(84f), chipTop + dp(26f))

        val listTop = chipTop + dp(36f)
        var listBottom = top + h - dp(12f)
        if (keyboardOpen) {
            val keyboardHeight = dp(184f)
            keyboardRect.set(contentLeft, listBottom - keyboardHeight, contentRight, listBottom)
            listBottom = keyboardRect.top - dp(8f)
        }
        listRect.set(contentLeft, listTop, contentRight, listBottom)
        listHeight = max(0f, listRect.height())

        scrollTrack.set(left + w - dp(6f), listTop, left + w - dp(2f), listBottom)

        categoryRects.clear()
        var y = top + dp(82f)
        for (category in ModuleRegistry.moduleCategories) {
            categoryRects[category] = RectF(
                sidebarRect.left + dp(10f),
                y,
                sidebarRect.right - dp(10f),
                y + dp(32f)
            )
            y += dp(32f)
        }

        generalRects.clear()
        y += dp(26f)
        for (module in ModuleRegistry.generalModules()) {
            generalRects[module.id] = RectF(
                sidebarRect.left + dp(10f),
                y,
                sidebarRect.right - dp(10f),
                y + dp(28f)
            )
            y += dp(28f)
        }
    }

    private fun rebuildLayout() {
        cards.clear()
        var y = 0f
        for (module in visibleModules()) {
            val isExpanded = expandedIds.contains(module.id)
            val rects = ArrayList<RectF>()
            var height = cardHeaderHeight()
            if (isExpanded) {
                for (setting in module.settings) {
                    val rowHeight = rowHeightFor(module, setting)
                    rects.add(
                        RectF(
                            contentLeft + dp(14f),
                            height,
                            contentRight - dp(14f),
                            height + rowHeight
                        )
                    )
                    height += rowHeight
                }
                height += dp(8f)
            }
            cards.add(Card(module, y, height, isExpanded, rects))
            y += height + dp(6f)
        }
        contentHeight = y
        maxScroll = max(0f, contentHeight - listHeight)
        scrollY = scrollY.coerceIn(0f, maxScroll)
        computeHueRect()
    }

    private fun computeHueRect() {
        hueRect.setEmpty()
        val key = activeColorKey ?: return
        for (card in cards) {
            if (!card.expanded) continue
            card.settingRects.forEachIndexed { index, rect ->
                val setting = card.module.settings.getOrNull(index) ?: return@forEachIndexed
                if (setting is Setting.Color && keyOf(card.module, setting) == key) {
                    hueRect.set(
                        rect.left + dp(14f),
                        rect.bottom - dp(18f),
                        rect.right - dp(14f),
                        rect.bottom - dp(6f)
                    )
                }
            }
        }
    }

    private fun keyOf(module: Module, setting: Setting): String = "${module.id}.${setting.id}"

    private fun rowHeightFor(module: Module, setting: Setting): Float = when (setting) {
        is Setting.Number -> dp(56f)
        is Setting.Color -> if (activeColorKey == keyOf(module, setting)) dp(72f) else dp(46f)
        is Setting.Block -> dp(42f)
        is Setting.Action -> dp(46f)
        is Setting.Label -> dp(40f)
        else -> dp(44f)
    }

    private fun visibleModules(): List<Module> {
        val query = searchQuery.trim().lowercase()
        var list: List<Module> = if (query.isNotEmpty()) {
            ModuleRegistry.modules.filter { it.category.isModuleCategory }
        } else {
            ModuleRegistry.inCategory(selectedCategory)
        }
        if (query.isNotEmpty()) {
            list = list.filter { module ->
                module.name.lowercase().contains(query) ||
                    module.description.lowercase().contains(query) ||
                    module.category.title.lowercase().contains(query) ||
                    module.settings.any { it.name.lowercase().contains(query) }
            }
        }
        if (enabledOnly) {
            list = list.filter { store.isEnabled(it) }
        }
        return list
    }

    // ------------------------------------------------------------------ painting

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        refreshAccent()

        val dim = (ModuleRegistry.findSetting("app_settings", "overlay_dim") as? Setting.Number)
            ?.let { store.number("app_settings", it) } ?: 55f
        canvas.drawColor(
            Color.argb((dim / 100f * 255f).roundToInt().coerceIn(0, 255), 2, 4, 10)
        )

        drawWindow(canvas)
        drawSidebar(canvas)
        drawHeader(canvas)
        drawSearch(canvas)
        drawChips(canvas)
        drawList(canvas)
        drawScrollbar(canvas)
        if (keyboardOpen) drawKeyboard(canvas)
    }

    private fun drawWindow(canvas: Canvas) {
        fill.color = windowColor
        canvas.drawRoundRect(windowRect, dp(16f), dp(16f), fill)
        stroke.color = cardStrokeColor
        stroke.strokeWidth = dp(1f)
        canvas.drawRoundRect(windowRect, dp(16f), dp(16f), stroke)
    }

    private fun drawSidebar(canvas: Canvas) {
        val radius = dp(16f)
        val path = Path()
        path.addRoundRect(
            sidebarRect,
            floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius),
            Path.Direction.CW
        )
        fill.color = sidebarColor
        canvas.drawPath(path, fill)

        stroke.color = 0xFF141B2B.toInt()
        stroke.strokeWidth = dp(1f)
        canvas.drawLine(
            sidebarRect.right, sidebarRect.top + dp(12f),
            sidebarRect.right, sidebarRect.bottom - dp(12f), stroke
        )

        // Crescent logo drawn from two circles so it needs no extra asset.
        val logoX = sidebarRect.left + dp(22f)
        val logoY = sidebarRect.top + dp(24f)
        fill.color = Color.rgb(255, 214, 102)
        canvas.drawCircle(logoX, logoY, dp(8f), fill)
        fill.color = sidebarColor
        canvas.drawCircle(logoX + dp(3.6f), logoY - dp(1.2f), dp(7.2f), fill)

        drawText(canvas, "Moonlight", sidebarRect.left + dp(36f), sidebarRect.top + dp(21f), sp(15f), textPrimary, medium)
        drawText(canvas, "v2.0  BEDROCK", sidebarRect.left + dp(36f), sidebarRect.top + dp(36f), sp(7.5f), textMuted, medium)

        drawSectionLabel(canvas, "MODULES", sidebarRect.left + dp(14f), sidebarRect.top + dp(66f))

        for (category in ModuleRegistry.moduleCategories) {
            val rect = categoryRects[category] ?: continue
            val selected = selectedCategory == category && searchQuery.isEmpty()
            drawSidebarRow(
                canvas = canvas,
                rect = rect,
                selected = selected,
                title = category.title,
                count = ModuleRegistry.count(category).toString(),
                category = category
            )
        }

        val firstGeneral = generalRects.values.firstOrNull()
        if (firstGeneral != null) {
            drawSectionLabel(canvas, "GENERAL", sidebarRect.left + dp(14f), firstGeneral.top - dp(10f))
        }
        for (module in ModuleRegistry.generalModules()) {
            val rect = generalRects[module.id] ?: continue
            val selected = selectedCategory == Category.GENERAL && expandedIds.contains(module.id)
            drawSidebarRow(
                canvas = canvas,
                rect = rect,
                selected = selected,
                title = module.name,
                count = "",
                category = Category.GENERAL
            )
        }
    }

    private fun drawSectionLabel(canvas: Canvas, label: String, x: Float, centerY: Float) {
        drawText(canvas, label, x, centerY, sp(7.5f), textMuted, bold)
    }

    private fun drawSidebarRow(
        canvas: Canvas,
        rect: RectF,
        selected: Boolean,
        title: String,
        count: String,
        category: Category
    ) {
        if (selected) {
            fill.color = Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(rect, dp(9f), dp(9f), fill)
            fill.color = accent
            canvas.drawRoundRect(
                rect.left, rect.top + dp(8f), rect.left + dp(2.5f), rect.bottom - dp(8f),
                dp(1.5f), dp(1.5f), fill
            )
        }
        val iconColor = if (selected) accent else textMuted
        drawCategoryIcon(canvas, category, rect.left + dp(17f), rect.centerY(), dp(11f), iconColor)
        drawText(
            canvas, ellipsize(title, rect.width() - dp(52f), sp(11.5f), regular),
            rect.left + dp(30f), rect.centerY(), sp(11.5f),
            if (selected) textPrimary else textSecondary, if (selected) medium else regular
        )
        if (count.isNotEmpty()) {
            drawTextRight(canvas, count, rect.right - dp(6f), rect.centerY(), sp(9f), textMuted, regular)
        }
    }

    private fun drawCategoryIcon(canvas: Canvas, category: Category, cx: Float, cy: Float, size: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = dp(1.4f)
        fill.color = color
        when (category) {
            Category.COMBAT -> {
                canvas.drawLine(cx - size / 2f, cy + size / 2f, cx + size / 2f, cy - size / 2f, stroke)
                canvas.drawLine(cx + size / 2f, cy + size / 2f, cx - size / 2f, cy - size / 2f, stroke)
            }
            Category.MOVEMENT -> {
                val first = Path()
                first.moveTo(cx - size / 2f, cy - size / 3f)
                first.lineTo(cx, cy)
                first.lineTo(cx - size / 2f, cy + size / 3f)
                canvas.drawPath(first, stroke)
                val second = Path()
                second.moveTo(cx + size / 8f, cy - size / 3f)
                second.lineTo(cx + size / 2f, cy)
                second.lineTo(cx + size / 8f, cy + size / 3f)
                canvas.drawPath(second, stroke)
            }
            Category.RENDER -> {
                canvas.drawOval(
                    RectF(cx - size / 2f, cy - size / 3f, cx + size / 2f, cy + size / 3f), stroke
                )
                canvas.drawCircle(cx, cy, size / 6f, fill)
            }
            Category.PLAYER -> {
                canvas.drawCircle(cx, cy - size / 5f, size / 4f, stroke)
                val body = Path()
                body.moveTo(cx - size / 2f, cy + size / 2f)
                body.quadTo(cx, cy - size / 10f, cx + size / 2f, cy + size / 2f)
                canvas.drawPath(body, stroke)
            }
            Category.BASE_FINDING -> {
                val house = Path()
                house.moveTo(cx - size / 2f, cy)
                house.lineTo(cx, cy - size / 2f)
                house.lineTo(cx + size / 2f, cy)
                house.lineTo(cx + size / 2f, cy + size / 2f)
                house.lineTo(cx - size / 2f, cy + size / 2f)
                house.close()
                canvas.drawPath(house, stroke)
            }
            Category.GENERAL -> {
                for (index in -1..1) {
                    val rowY = cy + index * size / 3f
                    canvas.drawLine(cx - size / 2f, rowY, cx + size / 2f, rowY, stroke)
                    val knobX = cx + if (index == 0) size / 6f else -size / 6f
                    canvas.drawCircle(knobX, rowY, size / 9f, fill)
                }
            }
        }
    }

    private fun drawHeader(canvas: Canvas) {
        val title = when {
            searchQuery.isNotEmpty() -> "Search"
            selectedCategory == Category.GENERAL -> "General"
            else -> "${selectedCategory.title} Modules"
        }
        drawText(canvas, title, contentLeft, windowRect.top + dp(22f), sp(19f), textPrimary, bold)

        val pool = if (searchQuery.isNotEmpty()) {
            ModuleRegistry.modules.filter { it.category.isModuleCategory }
        } else {
            ModuleRegistry.inCategory(selectedCategory)
        }
        val enabledCount = pool.count { store.isEnabled(it) }
        drawText(
            canvas,
            "${pool.size} modules  ·  $enabledCount enabled",
            contentLeft, windowRect.top + dp(37f), sp(8.5f), textMuted, regular
        )

        // Close control.
        stroke.color = cardStrokeColor
        stroke.strokeWidth = dp(1f)
        canvas.drawRoundRect(closeRect, dp(8f), dp(8f), stroke)
        stroke.color = textSecondary
        stroke.strokeWidth = dp(1.4f)
        val inset = dp(9f)
        canvas.drawLine(closeRect.left + inset, closeRect.top + inset, closeRect.right - inset, closeRect.bottom - inset, stroke)
        canvas.drawLine(closeRect.right - inset, closeRect.top + inset, closeRect.left + inset, closeRect.bottom - inset, stroke)
    }

    private fun drawSearch(canvas: Canvas) {
        fill.color = chipColor
        canvas.drawRoundRect(searchRect, dp(11f), dp(11f), fill)
        stroke.color = cardStrokeColor
        stroke.strokeWidth = dp(1f)
        canvas.drawRoundRect(searchRect, dp(11f), dp(11f), stroke)

        val iconX = searchRect.left + dp(15f)
        val iconY = searchRect.centerY()
        stroke.color = if (keyboardOpen) accent else textMuted
        stroke.strokeWidth = dp(1.5f)
        canvas.drawCircle(iconX, iconY, dp(4.2f), stroke)
        canvas.drawLine(iconX + dp(3.4f), iconY + dp(3.4f), iconX + dp(7f), iconY + dp(7f), stroke)

        val label = if (searchQuery.isEmpty()) "Search modules" else searchQuery
        val labelColor = if (searchQuery.isEmpty()) textMuted else textPrimary
        drawText(canvas, label, searchRect.left + dp(30f), iconY, sp(11f), labelColor, regular)
    }

    private fun drawChips(canvas: Canvas) {
        drawChip(canvas, allChipRect, "All", !enabledOnly)
        drawChip(canvas, enabledChipRect, "Enabled", enabledOnly)
    }

    private fun drawChip(canvas: Canvas, rect: RectF, label: String, selected: Boolean) {
        fill.color = if (selected) {
            Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent))
        } else {
            chipColor
        }
        canvas.drawRoundRect(rect, dp(13f), dp(13f), fill)
        if (selected) {
            stroke.color = accent
            stroke.strokeWidth = dp(1f)
            canvas.drawRoundRect(rect, dp(13f), dp(13f), stroke)
        }
        drawTextCenter(canvas, label, rect.centerX(), rect.centerY(), sp(9.5f), if (selected) accent else textMuted, medium)
    }

    private fun drawList(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(listRect)
        canvas.translate(0f, listRect.top - scrollY)
        for (card in cards) drawCard(canvas, card)
        canvas.restore()

        if (cards.isEmpty()) {
            val message = if (enabledOnly) "No enabled modules yet" else "Nothing matches that search"
            drawTextCenter(canvas, message, listRect.centerX(), listRect.centerY(), sp(11f), textMuted, regular)
        }
    }

    private fun drawCard(canvas: Canvas, card: Card) {
        val left = contentLeft
        val right = contentRight
        val top = card.top
        val bottom = top + card.height
        val round = dp(12f)

        fill.color = cardColor
        canvas.drawRoundRect(left, top, right, bottom, round, round, fill)
        stroke.color = cardStrokeColor
        stroke.strokeWidth = dp(1f)
        canvas.drawRoundRect(left, top, right, bottom, round, round, stroke)

        val headerCenter = top + cardHeaderHeight() / 2f
        val nameWidth = right - left - dp(150f)
        drawText(
            canvas, ellipsize(card.module.name, nameWidth, sp(12.5f), medium),
            left + dp(14f), headerCenter - dp(if (showDescriptions() && !compactCards()) 7f else 0f),
            sp(12.5f), textPrimary, medium
        )
        if (showDescriptions() && !compactCards()) {
            drawText(
                canvas,
                ellipsize(card.module.description, nameWidth, sp(9f), regular),
                left + dp(14f), headerCenter + dp(9f), sp(9f), textMuted, regular
            )
        }

        if (card.module.requiresHook && !RuntimeStatus.current.hooked) {
            val pill = RectF(right - dp(104f), headerCenter - dp(7f), right - dp(58f), headerCenter + dp(7f))
            fill.color = Color.argb(28, Color.red(warnColor), Color.green(warnColor), Color.blue(warnColor))
            canvas.drawRoundRect(pill, dp(7f), dp(7f), fill)
            drawTextCenter(canvas, "NO HOOK", pill.centerX(), pill.centerY(), sp(6.5f), warnColor, medium)
        }

        if (card.module.showToggle) {
            drawSwitch(canvas, right - dp(14f), headerCenter, store.isEnabled(card.module))
        }
        drawChevron(canvas, right - dp(34f), headerCenter, card.expanded)

        if (card.expanded) {
            drawSettings(canvas, card)
        }
    }

    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, expanded: Boolean) {
        stroke.color = textMuted
        stroke.strokeWidth = dp(1.4f)
        val half = dp(4f)
        val path = Path()
        if (expanded) {
            path.moveTo(cx - half, cy + half / 2f)
            path.lineTo(cx, cy - half / 2f)
            path.lineTo(cx + half, cy + half / 2f)
        } else {
            path.moveTo(cx - half, cy - half / 2f)
            path.lineTo(cx, cy + half / 2f)
            path.lineTo(cx + half, cy - half / 2f)
        }
        canvas.drawPath(path, stroke)
    }

    private fun drawSwitch(canvas: Canvas, right: Float, centerY: Float, on: Boolean) {
        val w = dp(40f)
        val h = dp(22f)
        val left = right - w
        val top = centerY - h / 2f
        fill.color = if (on) accent else 0xFF2A3350.toInt()
        canvas.drawRoundRect(left, top, right, top + h, h / 2f, h / 2f, fill)
        fill.color = if (on) Color.WHITE else 0xFF6B7590.toInt()
        val knobRadius = h / 2f - dp(2.5f)
        val knobX = if (on) right - h / 2f else left + h / 2f
        canvas.drawCircle(knobX, centerY, knobRadius, fill)
    }

    private fun drawSettings(canvas: Canvas, card: Card) {
        card.settingRects.forEachIndexed { index, rect ->
            val setting = card.module.settings.getOrNull(index) ?: return@forEachIndexed
            stroke.color = dividerColor
            stroke.strokeWidth = dp(1f)
            canvas.drawLine(rect.left, rect.top, rect.right, rect.top, stroke)
            drawSettingRow(canvas, card.module, setting, rect)
        }
    }

    private fun drawSettingRow(canvas: Canvas, module: Module, setting: Setting, rect: RectF) {
        val labelColor = textSecondary
        when (setting) {
            is Setting.Toggle -> {
                drawText(canvas, setting.name, rect.left, rect.centerY(), sp(10.5f), labelColor, regular)
                drawSwitch(canvas, rect.right - dp(2f), rect.centerY(), store.boolean(module.id, setting))
            }
            is Setting.Number -> {
                val value = store.number(module.id, setting)
                drawText(canvas, setting.name, rect.left, rect.top + dp(15f), sp(10.5f), labelColor, regular)
                drawTextRight(
                    canvas, setting.format(value), rect.right - dp(2f), rect.top + dp(15f),
                    sp(10f), accent, medium
                )
                val track = sliderTrack(rect)
                fill.color = 0xFF232B42.toInt()
                canvas.drawRoundRect(track, dp(2f), dp(2f), fill)
                val fraction = ((value - setting.min) / (setting.max - setting.min)).coerceIn(0f, 1f)
                val filled = RectF(track.left, track.top, track.left + track.width() * fraction, track.bottom)
                fill.color = accent
                canvas.drawRoundRect(filled, dp(2f), dp(2f), fill)
                fill.color = Color.WHITE
                canvas.drawCircle(
                    (track.left + track.width() * fraction).coerceIn(track.left, track.right),
                    track.centerY(), dp(5f), fill
                )
            }
            is Setting.Choice -> {
                drawText(
                    canvas, ellipsize(setting.name, rect.width() - dp(140f), sp(10.5f), regular),
                    rect.left, rect.centerY(), sp(10.5f), labelColor, regular
                )
                val value = setting.options.getOrElse(store.choiceIndex(module.id, setting)) { "" }
                val pill = RectF(rect.right - dp(112f), rect.centerY() - dp(12f), rect.right - dp(2f), rect.centerY() + dp(12f))
                fill.color = chipColor
                canvas.drawRoundRect(pill, dp(8f), dp(8f), fill)
                stroke.color = cardStrokeColor
                stroke.strokeWidth = dp(1f)
                canvas.drawRoundRect(pill, dp(8f), dp(8f), stroke)
                drawTextCenter(canvas, ellipsize(value, pill.width() - dp(12f), sp(9f), medium), pill.centerX(), pill.centerY(), sp(9f), accent, medium)
            }
            is Setting.Color -> {
                val color = store.color(module.id, setting)
                drawText(canvas, setting.name, rect.left, rect.centerY(), sp(10.5f), labelColor, regular)
                val chip = RectF(rect.right - dp(26f), rect.centerY() - dp(10f), rect.right - dp(6f), rect.centerY() + dp(10f))
                fill.color = color
                canvas.drawRoundRect(chip, dp(6f), dp(6f), fill)
                stroke.color = 0x55FFFFFF
                stroke.strokeWidth = dp(1f)
                canvas.drawRoundRect(chip, dp(6f), dp(6f), stroke)
                drawTextRight(
                    canvas, hexOf(color), chip.left - dp(8f), rect.centerY(),
                    sp(8.5f), textMuted, regular
                )
                if (activeColorKey == keyOf(module, setting)) {
                    val bar = hueRect
                    if (!bar.isEmpty) {
                        fill.shader = LinearGradient(
                            bar.left, bar.top, bar.right, bar.top,
                            intArrayOf(
                                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                                Color.BLUE, Color.MAGENTA, Color.RED
                            ),
                            null, Shader.TileMode.CLAMP
                        )
                        canvas.drawRoundRect(bar, dp(3f), dp(3f), fill)
                        fill.shader = null
                        val hue = FloatArray(3)
                        Color.colorToHSV(color, hue)
                        val markerX = bar.left + bar.width() * (hue[0] / 360f)
                        fill.color = Color.WHITE
                        canvas.drawCircle(markerX.coerceIn(bar.left, bar.right), bar.centerY(), dp(5.5f), fill)
                        stroke.color = 0xFF0A0E18.toInt()
                        stroke.strokeWidth = dp(1.5f)
                        canvas.drawCircle(markerX.coerceIn(bar.left, bar.right), bar.centerY(), dp(5.5f), stroke)
                    }
                }
            }
            is Setting.Block -> {
                val value = store.block(module.id, setting)
                drawText(
                    canvas, ellipsize(setting.name, rect.width() - dp(130f), sp(10.5f), regular),
                    rect.left, rect.centerY(), sp(10.5f), if (value.enabled) labelColor else textMuted, regular
                )
                val chip = RectF(rect.right - dp(76f), rect.centerY() - dp(9f), rect.right - dp(58f), rect.centerY() + dp(9f))
                fill.color = value.color
                canvas.drawRoundRect(chip, dp(5f), dp(5f), fill)
                drawSwitch(canvas, rect.right - dp(2f), rect.centerY(), value.enabled)
            }
            is Setting.Action -> {
                val button = RectF(rect.left, rect.top + dp(5f), rect.right, rect.bottom - dp(5f))
                fill.color = chipColor
                canvas.drawRoundRect(button, dp(9f), dp(9f), fill)
                stroke.color = Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent))
                stroke.strokeWidth = dp(1f)
                canvas.drawRoundRect(button, dp(9f), dp(9f), stroke)
                drawTextCenter(canvas, setting.name, button.centerX(), button.centerY(), sp(10f), accent, medium)
            }
            is Setting.Label -> {
                drawText(canvas, setting.name, rect.left, rect.centerY(), sp(10.5f), labelColor, regular)
                val raw = try {
                    setting.value()
                } catch (_: Throwable) {
                    "unavailable"
                }
                drawTextRight(
                    canvas, ellipsize(raw, rect.width() * 0.55f, sp(9f), regular),
                    rect.right, rect.centerY(), sp(9f), textMuted, regular
                )
            }
        }
    }

    private fun sliderTrack(rect: RectF): RectF =
        RectF(rect.left + dp(2f), rect.top + dp(34f), rect.right - dp(2f), rect.top + dp(40f))

    private fun drawScrollbar(canvas: Canvas) {
        if (contentHeight <= listHeight || maxScroll <= 0f) return
        fill.color = 0x1AFFFFFF
        canvas.drawRoundRect(scrollTrack, dp(2f), dp(2f), fill)

        val ratio = (listHeight / contentHeight).coerceIn(0f, 1f)
        val thumbHeight = max(dp(34f), listHeight * ratio)
        val offset = if (maxScroll > 0f) (scrollY / maxScroll) * (listHeight - thumbHeight) else 0f
        scrollThumb.set(scrollTrack.left, listRect.top + offset, scrollTrack.right, listRect.top + offset + thumbHeight)
        fill.color = Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(scrollThumb, dp(2f), dp(2f), fill)
    }

    private fun drawKeyboard(canvas: Canvas) {
        fill.color = 0xFF0C1220.toInt()
        canvas.drawRoundRect(keyboardRect, dp(12f), dp(12f), fill)
        stroke.color = cardStrokeColor
        stroke.strokeWidth = dp(1f)
        canvas.drawRoundRect(keyboardRect, dp(12f), dp(12f), stroke)

        keys.clear()
        val rows = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
        val keyHeight = dp(28f)
        val gap = dp(4f)
        val keyWidth = dp(26f)
        var y = keyboardRect.top + dp(10f)
        for (row in rows) {
            val totalWidth = row.length * keyWidth + (row.length - 1) * gap
            var x = keyboardRect.centerX() - totalWidth / 2f
            for (character in row) {
                val rect = RectF(x, y, x + keyWidth, y + keyHeight)
                keys.add(Key(character.toString(), rect))
                drawKey(canvas, rect, character.toString())
                x += keyWidth + gap
            }
            y += keyHeight + gap
        }

        val space = RectF(keyboardRect.centerX() - dp(74f), y, keyboardRect.centerX() + dp(26f), y + keyHeight)
        val backspace = RectF(space.right + gap, y, space.right + gap + dp(46f), y + keyHeight)
        val done = RectF(backspace.right + gap, y, backspace.right + gap + dp(48f), y + keyHeight)
        keys.add(Key("SPACE", space))
        keys.add(Key("DEL", backspace))
        keys.add(Key("DONE", done))
        drawKey(canvas, space, "space")
        drawKey(canvas, backspace, "del")
        fill.color = accent
        canvas.drawRoundRect(done, dp(6f), dp(6f), fill)
        drawTextCenter(canvas, "done", done.centerX(), done.centerY(), sp(9f), 0xFF071018.toInt(), medium)
    }

    private fun drawKey(canvas: Canvas, rect: RectF, label: String) {
        fill.color = chipColor
        canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
        drawTextCenter(canvas, label, rect.centerX(), rect.centerY(), sp(9f), textSecondary, regular)
    }

    // ------------------------------------------------------------------- text

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        centerY: Float,
        size: Float,
        color: Int,
        face: Typeface
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.typeface = face
        textPaint.textAlign = Paint.Align.LEFT
        val metrics = textPaint.fontMetrics
        canvas.drawText(value, x, centerY - (metrics.ascent + metrics.descent) / 2f, textPaint)
    }

    private fun drawTextRight(
        canvas: Canvas,
        value: String,
        x: Float,
        centerY: Float,
        size: Float,
        color: Int,
        face: Typeface
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.typeface = face
        textPaint.textAlign = Paint.Align.RIGHT
        val metrics = textPaint.fontMetrics
        canvas.drawText(value, x, centerY - (metrics.ascent + metrics.descent) / 2f, textPaint)
    }

    private fun drawTextCenter(
        canvas: Canvas,
        value: String,
        cx: Float,
        centerY: Float,
        size: Float,
        color: Int,
        face: Typeface
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.typeface = face
        textPaint.textAlign = Paint.Align.CENTER
        val metrics = textPaint.fontMetrics
        canvas.drawText(value, cx, centerY - (metrics.ascent + metrics.descent) / 2f, textPaint)
    }

    private fun ellipsize(value: String, maxWidth: Float, size: Float, face: Typeface): String {
        if (maxWidth <= 0f) return ""
        textPaint.textSize = size
        textPaint.typeface = face
        if (textPaint.measureText(value) <= maxWidth) return value
        var end = value.length
        while (end > 0) {
            val candidate = value.substring(0, end).trimEnd() + "…"
            if (textPaint.measureText(candidate) <= maxWidth) return candidate
            end--
        }
        return "…"
    }

    private fun hexOf(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastY = event.y
                moved = false
                dragMode = 0
                val hit = hitTest(event.x, event.y)
                if (hit is Hit.SettingRow && hit.control) {
                    when (val setting = hit.setting) {
                        is Setting.Number -> {
                            dragMode = 3
                            sliderModule = hit.module
                            sliderSetting = setting
                            sliderRect.set(sliderTrack(currentRowRect(hit) ?: RectF()))
                            applySlider(event.x)
                        }
                        is Setting.Color -> {
                            val row = currentRowRect(hit)
                            val insideHue = row != null && activeColorKey == keyOf(hit.module, setting) &&
                                hueRect.contains(event.x, contentYOf(event.y))
                            if (insideHue) {
                                dragMode = 4
                                hueModule = hit.module
                                hueSetting = setting
                                applyHue(event.x)
                            }
                        }
                        else -> {}
                    }
                } else if (hit == null || hit is Hit.CardBody) {
                    if (listRect.contains(event.x, event.y)) dragMode = 1
                } else if (hit is Hit.Scrollbar) {
                    dragMode = 2
                    applyScrollbar(event.y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > dp(6f) || abs(event.y - downY) > dp(6f)) moved = true
                when (dragMode) {
                    1 -> {
                        scrollY = (scrollY - (event.y - lastY)).coerceIn(0f, maxScroll)
                        invalidate()
                    }
                    2 -> applyScrollbar(event.y)
                    3 -> applySlider(event.x)
                    4 -> applyHue(event.x)
                }
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                lastTapX = event.x
                if (!moved && dragMode == 0) {
                    val hit = hitTest(event.x, event.y)
                    if (hit != null) handleTap(hit)
                }
                if (dragMode == 3 || dragMode == 4) {
                    store.persist()
                }
                dragMode = 0
                sliderModule = null
                sliderSetting = null
                hueModule = null
                hueSetting = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragMode = 0
                return true
            }
        }
        return true
    }

    private fun contentYOf(viewY: Float): Float = viewY - listRect.top + scrollY

    private fun currentRowRect(hit: Hit.SettingRow): RectF? {
        val card = cards.firstOrNull { it.module.id == hit.module.id } ?: return null
        val index = card.module.settings.indexOfFirst { it.id == hit.setting.id }
        if (index < 0) return null
        return card.settingRects.getOrNull(index)
    }

    private fun applySlider(x: Float) {
        val module = sliderModule ?: return
        val setting = sliderSetting ?: return
        if (sliderRect.width() <= 0f) return
        val fraction = ((x - sliderRect.left) / sliderRect.width()).coerceIn(0f, 1f)
        store.setNumber(module.id, setting, setting.min + fraction * (setting.max - setting.min))
        invalidate()
    }

    private fun applyHue(x: Float) {
        val module = hueModule ?: return
        val setting = hueSetting ?: return
        if (hueRect.width() <= 0f) return
        val fraction = ((x - hueRect.left) / hueRect.width()).coerceIn(0f, 1f)
        store.setColor(module.id, setting, Color.HSVToColor(floatArrayOf(fraction * 360f, 0.72f, 1f)))
        invalidate()
    }

    private fun applyScrollbar(y: Float) {
        if (maxScroll <= 0f) return
        val usable = max(1f, listHeight - scrollThumb.height())
        val fraction = ((y - listRect.top - scrollThumb.height() / 2f) / usable).coerceIn(0f, 1f)
        scrollY = fraction * maxScroll
        invalidate()
    }

    private fun hitTest(x: Float, y: Float): Hit? {
        if (keyboardOpen) {
            for (key in keys) {
                if (key.rect.contains(x, y)) return Hit.KeyPress(key.label)
            }
            if (keyboardRect.contains(x, y)) return null
        }
        if (closeRect.contains(x, y)) return Hit.Close
        if (searchRect.contains(x, y)) return Hit.Search
        if (allChipRect.contains(x, y)) return Hit.ChipAll
        if (enabledChipRect.contains(x, y)) return Hit.ChipEnabled
        if (scrollTrack.contains(x, y) && maxScroll > 0f) return Hit.Scrollbar

        for ((category, rect) in categoryRects) {
            if (rect.contains(x, y)) return Hit.CategoryRow(category)
        }
        for (module in ModuleRegistry.generalModules()) {
            val rect = generalRects[module.id] ?: continue
            if (rect.contains(x, y)) return Hit.GeneralRow(module)
        }

        if (!windowRect.contains(x, y)) {
            return if (closeOnOutside()) Hit.Outside else null
        }

        if (!listRect.contains(x, y)) return null
        val contentY = y - listRect.top + scrollY
        for (card in cards) {
            if (contentY < card.top || contentY > card.top + card.height) continue
            if (x < contentLeft || x > contentRight) continue

            card.settingRects.forEachIndexed { index, rect ->
                if (contentY >= rect.top && contentY <= rect.bottom) {
                    val setting = card.module.settings.getOrNull(index) ?: return@forEachIndexed
                    if (activeColorKey == keyOf(card.module, setting) && hueRect.contains(x, contentY)) {
                        return Hit.SettingRow(card.module, setting, true)
                    }
                    val control = when (setting) {
                        is Setting.Toggle -> x > rect.right - dp(52f)
                        is Setting.Block -> x > rect.right - dp(84f)
                        is Setting.Number -> contentY > rect.top + dp(24f)
                        is Setting.Choice -> x > rect.right - dp(120f)
                        is Setting.Color -> x > rect.right - dp(60f)
                        is Setting.Action -> true
                        else -> false
                    }
                    return Hit.SettingRow(card.module, setting, control)
                }
            }

            val switchRect = RectF(
                contentRight - dp(54f),
                card.top + cardHeaderHeight() / 2f - dp(14f),
                contentRight - dp(10f),
                card.top + cardHeaderHeight() / 2f + dp(14f)
            )
            if (card.module.showToggle && switchRect.contains(x, contentY)) {
                return Hit.ModuleSwitch(card.module)
            }
            return Hit.CardBody(card.module)
        }
        return null
    }

    private fun handleTap(hit: Hit) {
        refreshAccent()
        when (hit) {
            Hit.Close -> {
                haptic()
                onRequestClose()
            }
            Hit.Search -> {
                keyboardOpen = true
                computeMetrics()
                rebuildLayout()
                haptic()
                invalidate()
            }
            Hit.ChipAll -> {
                enabledOnly = false
                scrollY = 0f
                rebuildLayout()
                haptic()
                invalidate()
            }
            Hit.ChipEnabled -> {
                enabledOnly = true
                scrollY = 0f
                rebuildLayout()
                haptic()
                invalidate()
            }
            Hit.Outside -> {
                if (keyboardOpen) {
                    keyboardOpen = false
                    computeMetrics()
                    rebuildLayout()
                    invalidate()
                } else {
                    onRequestClose()
                }
            }
            is Hit.CategoryRow -> {
                selectedCategory = hit.category
                searchQuery = ""
                keyboardOpen = false
                scrollY = 0f
                activeColorKey = null
                computeMetrics()
                rebuildLayout()
                haptic()
                invalidate()
            }
            is Hit.GeneralRow -> {
                selectedCategory = Category.GENERAL
                expandedIds.add(hit.module.id)
                searchQuery = ""
                keyboardOpen = false
                scrollToModule(hit.module.id)
                haptic()
                invalidate()
            }
            is Hit.CardBody -> {
                if (hit.module.settings.isNotEmpty()) {
                    if (!expandedIds.add(hit.module.id)) expandedIds.remove(hit.module.id)
                } else if (hit.module.showToggle) {
                    toggleModule(hit.module)
                    return
                }
                activeColorKey = null
                rebuildLayout()
                haptic()
                invalidate()
            }
            is Hit.ModuleSwitch -> toggleModule(hit.module)
            is Hit.SettingRow -> handleSettingTap(hit)
            is Hit.KeyPress -> handleKey(hit.label)
            Hit.Scrollbar -> {}
        }
    }

    private fun scrollToModule(moduleId: String) {
        rebuildLayout()
        val card = cards.firstOrNull { it.module.id == moduleId } ?: return
        scrollY = (card.top - dp(8f)).coerceIn(0f, maxScroll)
    }

    private fun toggleModule(module: Module) {
        val next = !store.isEnabled(module)
        store.setEnabled(module.id, next)
        store.persist()
        onModuleToggled(module, next)
        haptic()
        invalidate()
    }

    private fun handleSettingTap(hit: Hit.SettingRow) {
        val module = hit.module
        val setting = hit.setting
        if (!hit.control) {
            if (setting is Setting.Color) {
                activeColorKey = if (activeColorKey == keyOf(module, setting)) null else keyOf(module, setting)
                rebuildLayout()
                invalidate()
            }
            return
        }
        var changed = true
        when (setting) {
            is Setting.Toggle -> store.setBoolean(module.id, setting, !store.boolean(module.id, setting))
            is Setting.Choice -> {
                val next = (store.choiceIndex(module.id, setting) + 1) % max(1, setting.options.size)
                store.setChoiceIndex(module.id, setting, next)
            }
            is Setting.Block -> {
                val current = store.block(module.id, setting)
                val row = currentRowRect(hit)
                val overChip = row != null && lastTapX < row.right - dp(56f)
                if (overChip) {
                    val index = GuiTheme.blockPalette.indexOfFirst { it == current.color }
                    val nextColor = GuiTheme.blockPalette[(index + 1).mod(GuiTheme.blockPalette.size)]
                    store.setBlock(module.id, setting, current.enabled, nextColor)
                } else {
                    store.setBlock(module.id, setting, !current.enabled, current.color)
                }
            }
            is Setting.Action -> {
                changed = false
                if (setting.id == "reset") {
                    store.resetAll()
                    activeColorKey = null
                    computeMetrics()
                    rebuildLayout()
                } else {
                    onAction(module, setting)
                }
                haptic()
                invalidate()
            }
            is Setting.Color -> {
                changed = false
                activeColorKey = if (activeColorKey == keyOf(module, setting)) null else keyOf(module, setting)
                rebuildLayout()
                invalidate()
            }
            is Setting.Number, is Setting.Label -> changed = false
        }
        if (changed) {
            store.persist()
            haptic()
            if (module.id == "app_settings" && setting.id == "gui_scale") {
                computeMetrics()
                rebuildLayout()
            }
            invalidate()
        }
    }

    private fun handleKey(label: String) {
        when (label) {
            "SPACE" -> searchQuery += " "
            "DEL" -> if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
            "DONE" -> {
                keyboardOpen = false
                computeMetrics()
            }
            else -> if (searchQuery.length < 24) searchQuery += label.lowercase()
        }
        scrollY = 0f
        rebuildLayout()
        invalidate()
    }
}
