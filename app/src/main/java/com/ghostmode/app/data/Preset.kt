package com.ghostmode.app.data

import androidx.annotation.StringRes
import com.ghostmode.app.R
import com.ghostmode.app.domain.NetworkMask

/**
 * A pair of shell command lists that turn Ghost Mode on and off.
 *
 * Built-in presets carry string resources instead of literal texts so they follow the app
 * language; custom presets store user-entered [title] / [description].
 */
data class Preset(
    val id: String,
    val title: String,
    val description: String,
    val onCommands: List<String>,
    val offCommands: List<String>,
    val networkMaskCaptureCommand: String?,
    val isBuiltIn: Boolean,
    @param:StringRes val titleRes: Int = 0,
    @param:StringRes val descriptionRes: Int = 0
)

/**
 * Placeholders understood by [com.ghostmode.app.domain.GhostModeController]:
 * - [MASK_PLACEHOLDER] — the network mask captured before the mode was turned on;
 * - [IMS_PACKAGES_PLACEHOLDER] — IMS service packages currently bound for the selected SIM slots;
 * - `-s 0` — rewritten to the selected SIM slot(s).
 */
object BuiltInPresets {
    const val ID_UNIVERSAL = "builtin_universal"
    const val ID_STOCK_PIXEL = "builtin_stock_pixel"
    const val ID_XIAOMI_HYPEROS = "builtin_xiaomi_hyperos"
    const val ID_SAMSUNG_ONE_UI = "builtin_samsung_one_ui"
    const val ID_ONEPLUS = "builtin_oneplus"
    const val ID_ORIGINOS = "builtin_originos"
    const val ID_LEGACY = "builtin_legacy"

    const val MASK_PLACEHOLDER = "{{SAVED_MASK}}"
    const val IMS_PACKAGES_PLACEHOLDER = "{{IMS_PACKAGES}}"
    const val SLOT_0 = "-s 0"

    const val MASK_CAPTURE_COMMAND = "cmd phone get-allowed-network-types-for-users $SLOT_0"
    const val IMS_DISABLE_COMMAND = "cmd phone ims disable $SLOT_0"
    const val IMS_ENABLE_COMMAND = "cmd phone ims enable $SLOT_0"
    const val GET_IMS_SERVICE_DEVICE_COMMAND = "cmd phone ims get-ims-service $SLOT_0 -d"
    const val GET_IMS_SERVICE_CARRIER_COMMAND = "cmd phone ims get-ims-service $SLOT_0 -c"
    const val CARRIER_CONFIG_REFRESH_COMMAND =
        "am broadcast -a android.telephony.action.CARRIER_CONFIG_CHANGED || true"

    const val SAMSUNG_IMS_PACKAGE = "com.sec.imsservice"
    const val SAMSUNG_IMS_PACKAGE_NEW = "com.samsung.android.imsservice"
    const val QUALCOMM_IMS_PACKAGE = "org.codeaurora.ims"
    const val MEDIATEK_IMS_PACKAGE = "com.mediatek.ims"

    /** `RILConstants.NETWORK_MODE_LTE_ONLY`. */
    const val NETWORK_MODE_LTE_ONLY = "11"

    /**
     * Restore value used only if the original `preferred_network_mode*` could not be read:
     * `NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA` (global incl. LTE). Normally the captured original
     * value is written back instead.
     */
    const val NETWORK_MODE_RESTORE_FALLBACK = "10"

    private const val IGNORE_FAILURE = " || true"
    private const val PM_DISABLE_USER = "pm disable-user --user 0"
    private const val PM_ENABLE = "pm enable"

    private val universal = Preset(
        id = ID_UNIVERSAL,
        title = "Universal",
        description = "",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypes(NetworkMask.LTE_ONLY),
            "$PM_DISABLE_USER $IMS_PACKAGES_PLACEHOLDER$IGNORE_FAILURE"
        ),
        offCommands = listOf(
            setAllowedNetworkTypes(MASK_PLACEHOLDER),
            IMS_ENABLE_COMMAND
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true,
        titleRes = R.string.preset_universal_title,
        descriptionRes = R.string.preset_universal_desc
    )

    private val stockPixel = Preset(
        id = ID_STOCK_PIXEL,
        title = "Stock / Pixel",
        description = "",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypes(NetworkMask.LTE_ONLY)
        ),
        offCommands = listOf(
            setAllowedNetworkTypes(MASK_PLACEHOLDER),
            IMS_ENABLE_COMMAND
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true,
        titleRes = R.string.preset_stock_title,
        descriptionRes = R.string.preset_stock_desc
    )

    private val xiaomiHyperOs = stockPixel.copy(
        id = ID_XIAOMI_HYPEROS,
        title = "Xiaomi HyperOS / MIUI",
        titleRes = R.string.preset_xiaomi_title,
        descriptionRes = R.string.preset_xiaomi_desc
    )

    private val samsungOneUi = Preset(
        id = ID_SAMSUNG_ONE_UI,
        title = "Samsung One UI",
        description = "",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypes(NetworkMask.LTE_ONLY),
            preferredNetworkMode("", NETWORK_MODE_LTE_ONLY),
            preferredNetworkMode("1", NETWORK_MODE_LTE_ONLY),
            preferredNetworkMode("2", NETWORK_MODE_LTE_ONLY),
            "$PM_DISABLE_USER $SAMSUNG_IMS_PACKAGE$IGNORE_FAILURE",
            "$PM_DISABLE_USER $SAMSUNG_IMS_PACKAGE_NEW$IGNORE_FAILURE",
            // Newer One UI releases may bind IMS from a differently named package.
            "$PM_DISABLE_USER $IMS_PACKAGES_PLACEHOLDER$IGNORE_FAILURE",
            "settings put global volte_vt_enabled 0",
            "settings put global enhanced_4g_mode_enabled 0",
            CARRIER_CONFIG_REFRESH_COMMAND
        ),
        offCommands = listOf(
            "settings put global volte_vt_enabled 1",
            "settings put global enhanced_4g_mode_enabled 1",
            IMS_ENABLE_COMMAND,
            preferredNetworkMode("", NETWORK_MODE_RESTORE_FALLBACK),
            preferredNetworkMode("1", NETWORK_MODE_RESTORE_FALLBACK),
            preferredNetworkMode("2", NETWORK_MODE_RESTORE_FALLBACK),
            setAllowedNetworkTypes(MASK_PLACEHOLDER),
            CARRIER_CONFIG_REFRESH_COMMAND
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true,
        titleRes = R.string.preset_samsung_title,
        descriptionRes = R.string.preset_samsung_desc
    )

    private val onePlusOxygenOs = Preset(
        id = ID_ONEPLUS,
        title = "OnePlus OxygenOS",
        description = "",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypes(NetworkMask.LTE_ONLY),
            "$PM_DISABLE_USER $QUALCOMM_IMS_PACKAGE$IGNORE_FAILURE",
            "$PM_DISABLE_USER $MEDIATEK_IMS_PACKAGE$IGNORE_FAILURE"
        ),
        offCommands = listOf(
            setAllowedNetworkTypes(MASK_PLACEHOLDER),
            IMS_ENABLE_COMMAND
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true,
        titleRes = R.string.preset_oneplus_title,
        descriptionRes = R.string.preset_oneplus_desc
    )

    private val vivoOriginOs = onePlusOxygenOs.copy(
        id = ID_ORIGINOS,
        title = "vivo / iQOO",
        titleRes = R.string.preset_vivo_title,
        descriptionRes = R.string.preset_vivo_desc
    )

    private val legacy = Preset(
        id = ID_LEGACY,
        title = "Android 9–11",
        description = "",
        onCommands = listOf(
            preferredNetworkMode("", NETWORK_MODE_LTE_ONLY),
            preferredNetworkMode("1", NETWORK_MODE_LTE_ONLY),
            preferredNetworkMode("2", NETWORK_MODE_LTE_ONLY),
            AIRPLANE_MODE_ENABLE,
            AIRPLANE_MODE_DISABLE
        ),
        offCommands = listOf(
            preferredNetworkMode("", NETWORK_MODE_RESTORE_FALLBACK),
            preferredNetworkMode("1", NETWORK_MODE_RESTORE_FALLBACK),
            preferredNetworkMode("2", NETWORK_MODE_RESTORE_FALLBACK),
            AIRPLANE_MODE_ENABLE,
            AIRPLANE_MODE_DISABLE
        ),
        networkMaskCaptureCommand = null,
        isBuiltIn = true,
        titleRes = R.string.preset_legacy_title,
        descriptionRes = R.string.preset_legacy_desc
    )

    private const val AIRPLANE_MODE_ENABLE = "cmd connectivity airplane-mode enable"
    private const val AIRPLANE_MODE_DISABLE = "cmd connectivity airplane-mode disable"

    val ALL: List<Preset> = listOf(
        universal,
        stockPixel,
        xiaomiHyperOs,
        samsungOneUi,
        onePlusOxygenOs,
        vivoOriginOs,
        legacy
    )

    const val DEFAULT_ID: String = ID_UNIVERSAL

    private fun setAllowedNetworkTypes(mask: String): String =
        "cmd phone set-allowed-network-types-for-users $SLOT_0 $mask"

    private fun preferredNetworkMode(suffix: String, mode: String): String =
        "settings put global preferred_network_mode$suffix $mode"
}
