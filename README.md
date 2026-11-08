# Moonlight

Moonlight is an Android overlay client for Minecraft Bedrock. A floating moon button
sits over the game, and tapping it opens a touch-first module panel with Combat,
Movement, Render, Player and Base Finding categories, a search field, a scrollbar and
an ON/OFF toggle on every module.

The project is a normal Android application written in Kotlin. It is **not** a
resource pack, behaviour pack or add-on, and it is **not** a modified Minecraft APK.

---

## Read this first: what can and cannot affect Minecraft

An ordinary Android app is sandboxed. It cannot read another app's memory, cannot
see Minecraft's world data and cannot draw into Minecraft's render pipeline. Every
module that needs world data (Storage ESP, Block ESP, Geode Finder, Light Source
Finder, Sus Chunk, Stash Finder, Logout Spots, Player ESP, Spectator Glow) and every
module that needs the camera or lighting (Fullbright, Freecam, Freelook) therefore
only works when code is running **inside Minecraft's own process**.

Moonlight handles this honestly:

- Every module can be toggled, configured, searched and exported today.
- The panel marks hook-only modules `NO HOOK` while no hook is attached, and the
  launcher screen shows the real host state.
- Nothing pretends to be editing the game. When a hook is attached, the badge
  disappears and the HUD reports `hook attached`.

Two legitimate ways to attach a hook:

1. **Rooted device.** A root loader injects a library into the Minecraft process
   (`com.mojang.minecraftpe`). Android's sandbox is bypassed by root, not by this app.
2. **Self-built patched host.** You build your own copy of a Bedrock-compatible host
   that loads the bridge library. Reverse engineering and redistributing Mojang's
   proprietary APK is not something this repository does or ships.

Without one of those, the app is a complete, installable configuration client for
those modules, and the overlay/HUD/eye-shortcut surfaces work normally.

### Hook contract

A hook that wants the panel to report it as live writes a heartbeat to the app's own
files directory:

```
/data/data/com.moonlight.bedrockclient/files/moonlight-bridge/status.json
```

```json
{ "pid": 12345, "version": "2.0.0", "timestamp": 1760000000000 }
```

The heartbeat counts as attached while `timestamp` is less than 120 seconds old and
`pid` is non-zero. `HostBridge.detect()` is the single reader.

---

## What is in the panel

| Category | Modules |
| --- | --- |
| Combat | Aimbot, Triggerbot, Reach, Velocity, Auto Clicker, Hitboxes |
| Movement | Flight, Freeze, Elytra Fly, No Fall, Sprint, Keep Sprint, Bhop, Scaffold |
| Render | Fullbright, Freecam, Freelook, HUD |
| Player | Player ESP, Spectator Glow |
| Base Finding | Storage ESP, Geode Finder, Light Source Finder, Sus Chunk Finder, Block ESP, Stash Finder, Logout Spots |
| General | Panel, Theme, Configs, Touch, About |

Highlights:

- **Storage ESP** ships a master toggle plus 17 individually toggleable block types
  (shulker boxes, chests, trapped chests, hoppers, ender chests, droppers,
  dispensers, smokers, furnaces, blast furnaces, note blocks, pistons, sticky
  pistons, barrels, item frames, crafters, brewing stands), each with its own color.
- **Block ESP** has its own block list, box style and tracer settings.
- **Freecam** exposes speed, no-clip, player lock, smooth camera, camera HUD,
  joystick size, button size, control opacity, joystick side and a reset action.- **Freelook** has hold/toggle activation, a sensitivity slider, and an eye shortcut that appears as a draggable bubble when the module is on.
- **Base finding** modules carry distance, threshold, tracer, diagnostics and
  player-modification filters.
- **Configs** exports and imports the whole configuration as JSON.

Everything above is real state: toggles persist in
`files/moonlight/config.json`, survive restarts and are shared between the panel,
the launcher screen and the settings screen.

---

## Install

1. Download `Moonlight-APK` from the latest **Actions** run, or build locally.
2. On the phone, allow installs from the app you are using to open the APK.
3. If an older build is installed, **uninstall it first**. Older builds were signed
   with a throwaway debug key, so Android refuses to replace them and Samsung
   reports "App not installed" / the package appears to be invalid.

Then open Moonlight, grant **Display over other apps**, start the overlay and launch
Minecraft. A moon button appears; tap it for the panel.

---

## Why the previous APK would not install or run

These were the underlying problems, all fixed in this tree:

1. **Signing changed on every CI run.** `assembleDebug` used the runner's
   auto-generated debug keystore, so every Actions artifact had a different
   signature. Installing a newer build over an older one fails with
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, which Samsung surfaces as "App not
   installed as package appears to be invalid". Release builds are now signed with a
   committed, stable key (`signing/moonlight-release.jks`), and debug builds use the
   same key, so updates install in place.
2. **Android 14 foreground service crash.** The manifest declared
   `foregroundServiceType="specialUse"` without the
   `FOREGROUND_SERVICE_SPECIAL_USE` permission and without the required
   `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property. `startForeground` threw
   a `SecurityException` and the service died immediately. Both are now declared,
   plus `POST_NOTIFICATIONS` for Android 13 and later.
3. **`QUERY_ALL_PACKAGES`** was used to find Minecraft. Replaced with a narrow
   `<queries>` entry for `com.mojang.minecraftpe`.
4. **Broken native payload.** The old CMake targets called `dlopen` inside
   Moonlight's own process, so they could never touch Minecraft, while still
   forcing an NDK download and two ABI build variants. An app that declares
   `arm64-v8a` and `armeabi-v7a` but ships mismatched native code is a common cause
   of install and launch failures on 32-bit capable devices. Those targets were
   removed. A new, honest native engine now ships **one** `arm64-v8a` library (see
   [Native engine](#native-engine)); the manifest advertises only that ABI, so the
   declared and shipped ABIs always match.
5. **A `wrap.sh` in `src/main/resources`** was never packaged into the APK and
   hinted at `LD_PRELOAD` injection that Android will not perform for you. Removed.
6. **Portrait-only activity** was wrong for a landscape game overlay. The launcher
   now follows device rotation and the overlay re-lays out on configuration change.
7. **No Gradle wrapper and no version pinning.** The build now pins AGP 8.1.1,
   Kotlin 1.9.0, Gradle 8.2 and Java 17, and CI installs API 34 and build-tools 34
   explicitly.

### About the signing key

`signing/moonlight-release.jks` and `signing/keystore.properties` are committed on
purpose so every CI run produces an update-compatible APK. This key only signs this
application; it is not a credential for any online service. If you would rather keep
the key out of the repository, delete both files, generate your own keystore and
either commit it or build locally. Without a key the release variant is unsigned and
Android will refuse to install it.

---

## Build

Local build needs JDK 17 and Android SDK platform 34 + build-tools 34.0.0:

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleRelease
```

If the SDK is not in a default location, either export `ANDROID_SDK_ROOT` or write
`sdk.dir=/path/to/android-sdk` into `local.properties` (git-ignored). The wrapper
fetches Gradle 8.2 on first run, so the first build also needs network access.

Output: `app/build/outputs/apk/release/app-release.apk`.

GitHub Actions builds the same variant on every push to `app/**` and uploads the
APK as the `Moonlight-APK` artifact after verifying the signature with `apksigner`.

### Verified build

This tree was built and checked with Temurin JDK 17.0.21, Android SDK platform 34
and build-tools 34.0.0:

```
BUILD SUCCESSFUL in 2m 14s
39 actionable tasks: 37 executed, 2 from cache
```

`apksigner verify` on the produced APK reports the committed Moonlight certificate,
and `aapt2 dump badging` reports `com.moonlight.bedrockclient`, versionCode 2,
versionName 2.0.0, minSdk 26, targetSdk 34, launchable `MainActivity`, and
`native-code: 'arm64-v8a'` — exactly the single library the build produces.

The APK has **not** been run against a live Minecraft install. Everything above is
build- and packaging-level verification; the in-game behaviour of hook-only modules
still depends on attaching a hook as described at the top of this file.

---

## Project layout

```
app/src/main/java/com/moonlight/bedrockclient/
├─ MainActivity.kt              launcher, permissions, host status
├─ AppSettingsActivity.kt       app-level settings bound to the same config
├─ FloatingOverlayService.kt    moon bubble, panel window, eye, HUD
├─ core/
│  ├─ Module.kt                 category, setting and module model
│  ├─ ModuleRegistry.kt         the full module catalogue
│  ├─ ConfigStore.kt            JSON persistence, import/export, ConfigHolder
│  ├─ HostBridge.kt             Minecraft install, root and hook heartbeat detection
│  └─ NativeEngine.kt           JNI front door, safe no-op without the library
└─ ui/
   ├─ ClickGuiView.kt           custom drawn panel, search keyboard, touch handling
   ├─ EyeHandleView.kt          Freelook eye shortcut bubble
   └─ GuiTheme.kt               accent presets and palette
app/src/main/cpp/
├─ CMakeLists.txt               single arm64-v8a shared library
├─ engine.cpp                  100 Hz scheduler thread and timing telemetry
├─ module_manager.cpp          thread-safe C++ mirror of the module catalogue
├─ hook_registry.cpp           attached/detached/stale bridge state
└─ jni_bridge.cpp              the JNI surface behind NativeEngine.kt
```

---

## Native engine

`app/src/main/cpp` builds `libmoonlight_engine.so` for **arm64-v8a only**, using the
pinned NDK `26.1.10909125` and CMake `3.22.1`.

| File | Role |
| --- | --- |
| `engine.cpp` | one worker thread at 100 Hz, start/stop, tick/mean/peak microsecond telemetry |
| `module_manager.cpp` | mutex-guarded registry of module ids, enabled flags and toggle counters |
| `hook_registry.cpp` | `attached` / `stale` / `detached` state for an in-process bridge |
| `jni_bridge.cpp` | the symbols `core/NativeEngine.kt` calls |

Wiring: `ConfigStore` pushes each toggle into the native mirror
(`NativeEngine.setEnabled`), and re-syncs the whole catalogue on load, import and
reset. The `About` page shows a `Native engine` row reading real counters from the
running thread (`arm64-v8a 100Hz <ticks> ticks, <us> mean, <n>/<m> enabled`).

**What it does not do:** change Minecraft. The library loads into Moonlight's own
process, so it has no access to the game's memory. `HookRegistry` therefore starts
`detached`, and only reports `attached` when `HostBridge` sees a genuine heartbeat at
`files/moonlight-bridge/status.json`. Hook-only modules keep their inactive label
until that happens; the native layer never fabricates the state.

Because the APK ships one arm64 library and declares only that ABI, it installs on
arm64 devices (which is what 64-bit Bedrock requires). On a 32-bit device
`NativeEngine.available` is `false` and every call is a no-op — though the APK itself
will not install there.

---

## Third-party archives

An earlier revision of this repository carried `LuminaClient-main.zip`, an unrelated
third-party Bedrock client (Pixie/ImGui GUI, protocol relay, Xbox auth). It was never
imported into this project: 0 of its 9,867 files are referenced anywhere in the
Gradle build or the Kotlin sources. The archive has been removed from the working
tree and `LuminaClient*.zip` is now git-ignored. It remains recoverable from git
history if you need it back.
