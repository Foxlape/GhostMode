package com.ghostmode.app.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.ghostmode.app.AppGraph
import com.ghostmode.app.GhostModeApp
import com.ghostmode.app.data.AppliedSnapshot
import com.ghostmode.app.data.BuiltInPresets
import com.ghostmode.app.data.CommandLogEntry
import com.ghostmode.app.data.GhostStateRepository
import com.ghostmode.app.data.PresetRepository
import com.ghostmode.app.ui.theme.GhostModeTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens to PNG. Skipped unless run with `-Pscreenshots`:
 * `./gradlew testDebugUnitTest -Pscreenshots --tests '*ScreenshotTest*'`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: GhostModeApp get() = ApplicationProvider.getApplicationContext()
    private val reviewDir = File(System.getProperty("ghost.screenshotDir") ?: "build/screenshots")
    private val metadataDir = System.getProperty("ghost.metadataDir")?.let(::File)
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L

    private val host = AppHost({}, {}, {}, {}, {})

    private companion object {
        val LOCALES = mapOf("en" to "en-US", "ru" to "ru-RU")
    }

    @Before
    fun onlyWhenRequested() {
        assumeTrue(System.getProperty("ghost.screenshots") == "true")
    }

    // Store listing screenshots (numbered) go to fastlane/metadata/android/<locale>/images/phoneScreenshots.
    @Test fun homeOn_dark_en() = shot("home_on_dark", "en", dark = true, on = true, listing = 1)
    @Test fun homeOn_dark_ru() = shot("home_on_dark", "ru", dark = true, on = true, listing = 1)
    @Test fun homeOff_dark_en() = shot("home_off_dark", "en", dark = true, on = false, listing = 2)
    @Test fun homeOff_dark_ru() = shot("home_off_dark", "ru", dark = true, on = false, listing = 2)
    @Test fun presets_dark_en() = shot("presets_dark", "en", dark = true, on = false, tab = Tab.PRESETS, listing = 3)
    @Test fun presets_dark_ru() = shot("presets_dark", "ru", dark = true, on = false, tab = Tab.PRESETS, listing = 3)
    @Test fun stats_dark_en() = shot("stats_dark", "en", dark = true, on = true, pages = listOf(Page.STATS), listing = 4)
    @Test fun stats_dark_ru() = shot("stats_dark", "ru", dark = true, on = true, pages = listOf(Page.STATS), listing = 4)
    @Test fun settings_dark_en() = shot("settings_dark", "en", dark = true, on = false, tab = Tab.SETTINGS, listing = 5)
    @Test fun settings_dark_ru() = shot("settings_dark", "ru", dark = true, on = false, tab = Tab.SETTINGS, listing = 5)
    @Test fun homeOn_light_en() = shot("home_on_light", "en", dark = false, on = true, listing = 6)
    @Test fun homeOn_light_ru() = shot("home_on_light", "ru", dark = false, on = true, listing = 6)

    // Review-only renders go to app/build/screenshots.
    @Test fun homeNoAccess_dark_ru() = shot("home_no_access_dark", "ru", dark = true, on = false, root = false)
    @Test fun log_dark_ru() = shot("log_dark", "ru", dark = true, on = true, pages = listOf(Page.LOG))
    @Test fun about_dark_ru() = shot("about_dark", "ru", dark = true, on = false, pages = listOf(Page.ABOUT))

    /** 512×512 launcher icon for store listings (fastlane `images/icon.png`). */
    @Test
    fun launcherIcon() {
        val drawable = app.getDrawable(com.ghostmode.app.R.mipmap.ic_launcher)!!
        val bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 512, 512)
        drawable.draw(android.graphics.Canvas(bitmap))
        LOCALES.values.forEach { locale ->
            val file = File(metadataDir ?: reviewDir, "$locale/images/icon.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun shot(
        name: String,
        lang: String,
        dark: Boolean,
        on: Boolean,
        root: Boolean = true,
        tab: Tab = Tab.HOME,
        pages: List<Page> = emptyList(),
        listing: Int? = null
    ) {
        RuntimeEnvironment.setQualifiers("+$lang-${if (dark) "night" else "notnight"}")
        seedPreferences(on)
        val graph = AppGraph(app)
        graph.root.pinAvailabilityForTest(root)
        if (on) seedLog(graph)
        app.replaceGraphForTest(graph)
        val vm = MainViewModel(app)

        compose.setContent {
            GhostModeTheme(darkTheme = dark) { GhostApp(vm, host, tab, pages) }
        }
        compose.waitForIdle()
        val target = if (listing != null && metadataDir != null) {
            File(metadataDir, "${LOCALES.getValue(lang)}/images/phoneScreenshots/${listing}_$name.png")
        } else {
            File(reviewDir, "${name}_$lang.png")
        }
        target.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(target.path)
    }

    private fun seedPreferences(on: Boolean) {
        val state = app.getSharedPreferences(GhostStateRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val sessions = JSONArray()
        // A week of nights plus a couple of daytime sessions.
        for (day in 6 downTo 1) {
            val start = now - day * 24 * hour - (3 + day % 3) * hour
            sessions.put(JSONObject().put("startMs", start).put("endMs", start + (4 + day % 4) * hour + 17 * 60_000L))
        }
        if (on) sessions.put(JSONObject().put("startMs", now - 2 * hour - 13 * 60_000L).put("endMs", 0L))
        val editor = state.edit()
            .clear()
            .putString("sessions", sessions.toString())
            .putBoolean("schedule_enabled", true)
            .putInt("schedule_start_minute", 23 * 60)
            .putInt("schedule_end_minute", 7 * 60 + 30)
            .putBoolean("notification_enabled", true)
            .putString("active_preset_id", BuiltInPresets.ID_UNIVERSAL)
        if (on) {
            val snapshot = AppliedSnapshot(
                presetId = BuiltInPresets.ID_UNIVERSAL,
                offCommands = BuiltInPresets.ALL.first().offCommands,
                slots = listOf(0, 1),
                settingsOriginals = emptyMap(),
                disabledPackages = emptyList(),
                bootCount = AppliedSnapshot.BOOT_COUNT_UNKNOWN,
                appliedAtMs = now - 2 * hour
            )
            editor.putBoolean("is_on", true)
                .putLong("is_on_timestamp", now - 2 * hour - 13 * 60_000L)
                .putString("applied_snapshot", snapshot.toJson())
                .putLong("timer_fire_at", now + 47 * 60_000L)
        }
        editor.commit()

        val presets = app.getSharedPreferences(PresetRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val custom = JSONArray().put(
            JSONObject()
                .put("id", "custom_demo")
                .put("title", "Pixel 8 · work SIM")
                .put("description", "Stock commands for SIM 2 only, plus a carrier config reload.")
                .put("onCommands", JSONArray(listOf("cmd phone ims disable -s 0", "cmd phone set-allowed-network-types-for-users -s 0 01000001000000000000")))
                .put("offCommands", JSONArray(listOf("cmd phone set-allowed-network-types-for-users -s 0 {{SAVED_MASK}}", "cmd phone ims enable -s 0")))
        )
        presets.edit().clear().putString("custom_presets", custom.toString()).commit()
    }

    private fun seedLog(graph: AppGraph) {
        val base = now - 2 * hour - 13 * 60_000L
        listOf(
            Triple("cmd phone get-allowed-network-types-for-users -s 0", "11001111101111111111", 0),
            Triple("cmd phone ims disable -s 0", "", 0),
            Triple("cmd phone ims disable -s 1", "", 0),
            Triple("cmd phone set-allowed-network-types-for-users -s 0 01000001000000000000", "", 0),
            Triple("cmd phone set-allowed-network-types-for-users -s 1 01000001000000000000", "", 0),
            Triple("cmd phone ims get-ims-service -s 0 -d", "com.shannon.imsservice", 0),
            Triple("pm disable-user --user 0 com.shannon.imsservice || true", "Package com.shannon.imsservice new state: disabled-user", 0),
            Triple("cmd phone ims get-ims-service -s 1 -c", "Exception: slot 1 has no active subscription", 255)
        ).forEachIndexed { index, (command, out, code) ->
            graph.state.appendLog(CommandLogEntry(base + index * 400L, command, out, "", code))
        }
    }
}
