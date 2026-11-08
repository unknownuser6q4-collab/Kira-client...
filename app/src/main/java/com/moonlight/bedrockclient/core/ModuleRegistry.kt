package com.moonlight.bedrockclient.core

import com.moonlight.bedrockclient.BuildConfig

/**
 * The shipped module catalogue.
 *
 * Every entry here is a real configuration object: toggles and values are stored,
 * searched and exported. Modules marked [Module.requiresHook] only affect the game
 * when the in-game hook is attached, and the UI says so instead of faking it.
 */
object ModuleRegistry {

    // ------------------------------------------------------------------ helpers

    private fun toggle(id: String, name: String, default: Boolean = false) =
        Setting.Toggle(id, name, default)

    private fun num(
        id: String,
        name: String,
        default: Float,
        min: Float,
        max: Float,
        step: Float = 1f,
        unit: String = ""
    ) = Setting.Number(id, name, default, min, max, step, unit)

    private fun choice(id: String, name: String, vararg options: String) =
        Setting.Choice(id, name, options.toList(), 0)

    private fun choice(id: String, name: String, defaultIndex: Int, vararg options: String) =
        Setting.Choice(id, name, options.toList(), defaultIndex)

    private fun color(id: String, name: String, value: Long) = Setting.Color(id, name, value.toInt())

    private fun action(id: String, name: String) = Setting.Action(id, name)

    private fun label(id: String, name: String, value: () -> String) = Setting.Label(id, name, value)

    private fun block(id: String, name: String, value: Long, enabled: Boolean = true) =
        Setting.Block(id, name, enabled, value.toInt())

    // ------------------------------------------------------------------- render

    private fun renderModules(): List<Module> = listOf(
        Module(
            id = "fullbright",
            name = "Fullbright",
            description = "Lifts darkness in caves, night and unlit rooms",
            category = Category.RENDER,
            settings = listOf(
                num("brightness", "Brightness", 15f, 1f, 15f, 1f),
                toggle("keep_lighting", "Keep world lighting", true),
                toggle("smooth", "Smooth transition", true),
                num("fade", "Fade time", 250f, 0f, 1000f, 50f, "ms")
            )
        ),
        Module(
            id = "freecam",
            name = "Freecam",
            description = "Detach the camera and fly it independently",
            category = Category.RENDER,
            settings = listOf(
                num("speed", "Camera speed", 2f, 0.5f, 8f, 0.1f),
                toggle("no_clip", "No clip", true),
                toggle("player_lock", "Player lock", true),
                toggle("smooth", "Smooth camera", true),
                toggle("camera_hud", "Camera HUD", true),
                num("joystick_size", "Joystick size", 72f, 40f, 120f, 2f, "dp"),
                num("button_size", "Button size", 54f, 30f, 90f, 2f, "dp"),
                num("control_opacity", "Control opacity", 70f, 20f, 100f, 5f, "%"),
                choice("side", "Joystick side", 0, "Left", "Right"),
                toggle("vertical_buttons", "Up and down buttons", true),
                action("reset_camera", "Reset camera")
            )
        ),
        Module(
            id = "freelook",
            name = "Freelook",
            description = "Orbit the camera without turning the player",
            category = Category.RENDER,
            settings = listOf(
                choice("activation", "Activation", 0, "Toggle", "Hold"),
                num("sensitivity", "Sensitivity", 1.4f, 0.5f, 3f, 0.1f),
                toggle("eye_shortcut", "Eye shortcut", true),
                choice("eye_side", "Eye position", 1, "Left", "Right"),
                num("eye_size", "Eye size", 44f, 28f, 64f, 2f, "dp"),
                toggle("keep_facing", "Keep player facing", true),
                action("spawn_eye", "Show eye shortcut")
            )
        ),
        Module(
            id = "hud",
            name = "HUD",
            description = "Overlay readout of enabled modules and hook state",
            category = Category.RENDER,
            requiresHook = false,
            settings = listOf(
                choice("position", "Position", 1, "Top left", "Top right", "Bottom left", "Bottom right"),
                num("scale", "Scale", 100f, 60f, 160f, 5f, "%"),
                num("background", "Background", 45f, 0f, 100f, 5f, "%"),
                toggle("show_hook", "Show hook status", true),
                toggle("watermark", "Show watermark", true)
            )
        )
    )

    // ------------------------------------------------------------- base finding

    private fun storageEspModule(): Module = Module(
        id = "storage_esp",
        name = "Storage ESP",
        description = "Glowing 3D outlines around containers and storage blocks",
        category = Category.BASE_FINDING,
        settings = listOf(
            toggle("through_walls", "Through walls", true),
            choice("box_style", "Box style", 0, "Outline", "Outline + fill", "Fill"),
            num("max_distance", "Max distance", 128f, 16f, 256f, 8f, "m"),
            num("glow", "Glow strength", 6f, 1f, 10f, 1f),
            toggle("tracers", "Tracers", false),
            toggle("labels", "Distance labels", false),
            block("shulker", "Shulker Box", 0xFFB45CFF),
            block("chest", "Chest", 0xFFFF9A3C),
            block("trapped_chest", "Trapped Chest", 0xFFFF8A2C),
            block("hopper", "Hopper", 0xFFFFD166),
            block("ender_chest", "Ender Chest", 0xFF9B6BFF),
            block("dropper", "Dropper", 0xFF8FA3FF),
            block("dispenser", "Dispenser", 0xFF6FA8FF),
            block("smoker", "Smoker", 0xFF9AA3B2),
            block("furnace", "Furnace", 0xFFC9A27E),
            block("blast_furnace", "Blast Furnace", 0xFF8C929C),
            block("noteblock", "Note Block", 0xFF7BD88F),
            block("piston", "Piston", 0xFFA98B6B),
            block("sticky_piston", "Sticky Piston", 0xFF8FBF6B),
            block("barrel", "Barrel", 0xFFD2A05C),
            block("item_frame", "Item Frame", 0xFFE7C36A),
            block("crafter", "Crafter", 0xFFC77DFF),
            block("brewing_stand", "Brewing Stand", 0xFFE0B0FF)
        )
    )

    private fun baseFindingModules(): List<Module> = listOf(
        storageEspModule(),
        Module(
            id = "geode_finder",
            name = "Geode Finder",
            description = "Amethyst geodes with stage and base-modification diagnostics",
            category = Category.BASE_FINDING,
            settings = listOf(
                num("max_distance", "Max distance", 256f, 32f, 512f, 16f, "m"),
                color("geode_color", "Geode color", 0xFFC77DFF),
                toggle("diagnostics", "Distance and stage details", true),
                toggle("bounding_box", "3D bounding box", true),
                toggle("tracers", "Tracers", false),
                toggle("filter_modified", "Flag player-modified geodes", true),
                num("min_mature", "Min mature clusters", 0f, 0f, 12f, 1f)
            )
        ),
        Module(
            id = "light_finder",
            name = "Light Source Finder",
            description = "Wireframe volumes around player-placed light sources",
            category = Category.BASE_FINDING,
            settings = listOf(
                color("light_color", "Highlight color", 0xFFFFE066),
                num("max_distance", "Max distance", 128f, 16f, 256f, 8f, "m"),
                toggle("through_walls", "Through walls", true),
                toggle("volumetric", "Volumetric grid", true),
                toggle("player_only", "Player-placed only", true),
                toggle("show_level", "Show light level", false)
            )
        ),
        Module(
            id = "sus_chunk",
            name = "Sus Chunk Finder",
            description = "Scores loaded chunks for player activity and bases",
            category = Category.BASE_FINDING,
            settings = listOf(
                num("threshold", "Confidence threshold", 60f, 10f, 100f, 5f, "%"),
                color("sus_color", "Tracer color", 0xFFFF5C8A),
                toggle("tracers", "Directional tracers", true),
                toggle("bounding_box", "Chunk bounding box", true),
                toggle("confidence", "Show confidence", true),
                num("min_signals", "Min signal types", 4f, 1f, 10f, 1f)
            )
        ),
        Module(
            id = "block_esp",
            name = "Block ESP",
            description = "Wireframes for chosen block IDs through solid terrain",
            category = Category.BASE_FINDING,
            settings = listOf(
                num("max_distance", "Max distance", 128f, 16f, 256f, 8f, "m"),
                toggle("through_walls", "Through walls", true),
                toggle("tracers", "Tracers", false),
                choice("box_style", "Box style", 0, "Outline", "Outline + fill", "Fill"),
                block("ancient_debris", "Ancient Debris", 0xFF8B5A2B),
                block("diamond_ore", "Diamond Ore", 0xFF4DE1E1),
                block("deepslate_diamond", "Deepslate Diamond", 0xFF3CC9C9),
                block("ender_chest", "Ender Chest", 0xFF9B6BFF),
                block("mob_spawner", "Mob Spawner", 0xFF59C8FF),
                block("gold_ore", "Gold Ore", 0xFFFFD166),
                block("emerald_ore", "Emerald Ore", 0xFF57D9A3),
                block("nether_gold", "Nether Gold Ore", 0xFFE0B25C, enabled = false)
            )
        ),
        Module(
            id = "stash_finder",
            name = "Stash Finder",
            description = "Flags container clusters and logs their coordinates",
            category = Category.BASE_FINDING,
            settings = listOf(
                num("threshold", "Container threshold", 8f, 2f, 32f, 1f),
                num("radius", "Scan radius", 64f, 16f, 128f, 8f, "m"),
                color("stash_color", "Highlight color", 0xFFFFB84D),
                toggle("chat_log", "Chat log", true),
                toggle("hud_alert", "HUD alert", true),
                toggle("file_log", "Write to log file", true)
            )
        ),
        Module(
            id = "logout_spots",
            name = "Logout Spots",
            description = "Marks where tracked players disconnect",
            category = Category.BASE_FINDING,
            settings = listOf(
                color("marker_color", "Marker color", 0xFFFF4D6D),
                toggle("skeleton", "Ghost skeleton", true),
                toggle("nametag", "Nametag", true),
                num("max_age", "Keep spots for", 6f, 1f, 48f, 1f, "h"),
                toggle("tracers", "Tracers", false),
                toggle("chat_notify", "Chat notification", true)
            )
        )
    )

    // ------------------------------------------------------------------- player

    private fun playerModules(): List<Module> = listOf(
        Module(
            id = "player_esp",
            name = "Player ESP",
            description = "Boxes, tracers and detailed nametags through terrain",
            category = Category.PLAYER,
            settings = listOf(
                toggle("boxes", "Bounding boxes", true),
                toggle("tracers", "Tracers", true),
                toggle("nametags", "Nametags", true),
                toggle("health", "Health bars", true),
                toggle("armor", "Armor durability", true),
                toggle("held_items", "Held items", true),
                toggle("distance", "Exact distance", true),
                toggle("through_walls", "Through walls", true),
                num("max_distance", "Max distance", 128f, 16f, 256f, 8f, "m"),
                color("friend_color", "Friend color", 0xFF6BE38A),
                color("enemy_color", "Enemy color", 0xFFFF4D6D)
            )
        ),
        Module(
            id = "spectator_glow",
            name = "Spectator Glow",
            description = "Glows players who are in spectator mode",
            category = Category.PLAYER,
            settings = listOf(
                color("glow_color", "Glow color", 0xFFFFD966),
                toggle("only_spectators", "Only spectators", true),
                num("intensity", "Glow intensity", 5f, 1f, 10f, 1f),
                toggle("through_walls", "Through walls", true)
            )
        )
    )

    // ------------------------------------------------------------------- combat

    private fun combatModules(): List<Module> = listOf(
        Module(
            id = "aimbot",
            name = "Aimbot",
            description = "Smoothly moves your aim toward a target",
            category = Category.COMBAT,
            settings = listOf(
                num("fov", "Field of view", 90f, 10f, 180f, 5f, "°"),
                num("smoothing", "Smoothing", 6f, 1f, 10f, 1f),
                toggle("visible_only", "Visible targets only", true),
                toggle("team_check", "Ignore teammates", true)
            )
        ),
        Module(
            id = "triggerbot",
            name = "Triggerbot",
            description = "Attacks when the crosshair is on a target",
            category = Category.COMBAT,
            settings = listOf(
                num("delay", "Attack delay", 120f, 0f, 500f, 10f, "ms"),
                num("range", "Range", 4.5f, 1f, 8f, 0.1f, "b"),
                toggle("only_weapon", "Only with a weapon", true)
            )
        ),
        Module(
            id = "reach",
            name = "Reach",
            description = "Extends interaction distance",
            category = Category.COMBAT,
            settings = listOf(
                num("distance", "Reach distance", 4.5f, 3f, 8f, 0.1f, "b"),
                toggle("only_players", "Players only", false)
            )
        ),
        Module(
            id = "velocity",
            name = "Velocity",
            description = "Reduces knockback taken from hits",
            category = Category.COMBAT,
            settings = listOf(
                num("horizontal", "Horizontal", 0f, 0f, 100f, 5f, "%"),
                num("vertical", "Vertical", 0f, 0f, 100f, 5f, "%")
            )
        ),
        Module(
            id = "autoclicker",
            name = "Auto Clicker",
            description = "Clicks for you at a chosen rate",
            category = Category.COMBAT,
            settings = listOf(
                num("cps", "Clicks per second", 12f, 1f, 25f, 1f),
                toggle("only_held", "Only while holding", true)
            )
        ),
        Module(
            id = "hitbox",
            name = "Hitboxes",
            description = "Highlights target hitboxes",
            category = Category.COMBAT,
            settings = listOf(
                num("scale", "Box scale", 1.5f, 1f, 3f, 0.1f),
                color("hitbox_color", "Hitbox color", 0xFFFF5C7A)
            )
        )
    )

    // ----------------------------------------------------------------- movement

    private fun movementModules(): List<Module> = listOf(
        Module(
            id = "flight",
            name = "Flight",
            description = "Fly freely in any direction",
            category = Category.MOVEMENT,
            settings = listOf(
                num("speed", "Speed", 1.5f, 0.5f, 5f, 0.1f),
                toggle("anti_kick", "Anti kick", true)
            )
        ),
        Module(
            id = "freeze",
            name = "Freeze",
            description = "Server sees you standing still while you move",
            category = Category.MOVEMENT,
            settings = listOf(toggle("sneak", "Hold sneak", false))
        ),
        Module(
            id = "elytra_fly",
            name = "Elytra Fly",
            description = "Elytra flight controls without fireworks",
            category = Category.MOVEMENT,
            settings = listOf(
                num("speed", "Speed", 1.8f, 0.5f, 5f, 0.1f),
                toggle("auto_start", "Auto start glide", true)
            )
        ),
        Module(
            id = "no_fall",
            name = "No Fall",
            description = "Cancels fall damage",
            category = Category.MOVEMENT,
            settings = listOf(toggle("only_falling", "Only while falling", true))
        ),
        Module(
            id = "sprint",
            name = "Sprint",
            description = "Always sprint while moving",
            category = Category.MOVEMENT,
            settings = listOf(toggle("omni", "Omni sprint", false))
        ),
        Module(
            id = "keep_sprint",
            name = "Keep Sprint",
            description = "Keeps sprint through collisions",
            category = Category.MOVEMENT,
            settings = listOf(toggle("restore", "Restore after hit", true))
        ),
        Module(
            id = "bhop",
            name = "Bhop",
            description = "Auto-jumps while you move",
            category = Category.MOVEMENT,
            settings = listOf(toggle("auto_jump", "Auto jump", true))
        ),
        Module(
            id = "scaffold",
            name = "Scaffold",
            description = "Places blocks under your feet as you walk",
            category = Category.MOVEMENT,
            settings = listOf(
                choice("mode", "Mode", 0, "Normal", "Tower", "Expand"),
                num("reach", "Placement reach", 4.5f, 1f, 8f, 0.1f, "b"),
                toggle("keep_y", "Keep same Y level", true)
            )
        )
    )

    // ------------------------------------------------------------------ general

    private fun generalPages(): List<Module> = listOf(
        Module(
            id = "app_settings",
            name = "Panel",
            description = "Floating panel scale, dimming and feedback",
            category = Category.GENERAL,
            requiresHook = false,
            showToggle = false,
            settings = listOf(
                num("gui_scale", "GUI scale", 100f, 70f, 150f, 5f, "%"),
                num("overlay_dim", "Overlay dim", 55f, 0f, 90f, 5f, "%"),
                toggle("haptics", "Haptic feedback", true),
                toggle("descriptions", "Show descriptions", true)
            )
        ),
        Module(
            id = "app_theme",
            name = "Theme",
            description = "Accent color and card density",
            category = Category.GENERAL,
            requiresHook = false,
            showToggle = false,
            settings = listOf(
                choice(
                    "accent", "Accent", 0,
                    "Moonlight", "Kuro", "Spectre", "Emerald", "Crimson", "Amber"
                ),
                toggle("compact", "Compact cards", false)
            )
        ),
        Module(
            id = "app_configs",
            name = "Configs",
            description = "Export, import and reset the stored configuration",
            category = Category.GENERAL,
            requiresHook = false,
            showToggle = false,
            settings = listOf(
                label("path", "Config file") { RuntimeStatus.configPath },
                label("enabled", "Enabled modules") {
                    "${RuntimeStatus.enabledCount} of ${moduleCount()}"
                },
                action("export", "Export config"),
                action("import", "Import config"),
                action("reset", "Reset all settings")
            )
        ),
        Module(
            id = "app_touch",
            name = "Touch",
            description = "How the overlay behaves on a touchscreen",
            category = Category.GENERAL,
            requiresHook = false,
            showToggle = false,
            settings = listOf(
                label("moon", "Moon button") { "Drag to move, tap to open" },
                label("panel", "Panel") { "Drag list to scroll, tap outside to close" },
                label("search", "Search") { "Opens the built-in keyboard" },
                toggle("close_outside", "Close on outside tap", true)
            )
        ),
        Module(
            id = "app_about",
            name = "About",
            description = "Build, host and hook diagnostics",
            category = Category.GENERAL,
            requiresHook = false,
            showToggle = false,
            settings = listOf(
                label("version", "Client version") { BuildConfig.VERSION_NAME },
                label("minecraft", "Minecraft") {
                    if (RuntimeStatus.current.minecraftInstalled) {
                        "v${RuntimeStatus.current.minecraftVersion}"
                    } else {
                        "not installed"
                    }
                },
                label("hook", "In-game hook") { HostBridge.describe(RuntimeStatus.current) },
                label("root", "Root") {
                    if (RuntimeStatus.current.rootDetected) "su detected" else "no su binary"
                },
                label("bridge", "Bridge heartbeat") {
                    val status = RuntimeStatus.current
                    if (status.hooked) "${status.hookAgeSeconds}s ago" else "never"
                },
                label("native", "Native engine") { NativeEngine.describe() },
                action("recheck", "Re-check host")
            )
        )
    )

    // -------------------------------------------------------------------- index

    val modules: List<Module> = buildList {
        addAll(combatModules())
        addAll(movementModules())
        addAll(renderModules())
        addAll(playerModules())
        addAll(baseFindingModules())
        addAll(generalPages())
    }

    val moduleCategories: List<Category> = Category.values().filter { it.isModuleCategory }

    fun module(id: String): Module? = modules.firstOrNull { it.id == id }

    fun inCategory(category: Category): List<Module> = modules.filter { it.category == category }

    fun generalModules(): List<Module> = modules.filter { it.category == Category.GENERAL }

    fun count(category: Category): Int = modules.count { it.category == category }

    fun findSetting(moduleId: String, settingId: String): Setting? =
        module(moduleId)?.settings?.firstOrNull { it.id == settingId }

    fun moduleCount(): Int = modules.count { it.category.isModuleCategory }
}
