package com.ghostmode.app.data

data class Preset(
    val id: String,
    val title: String,
    val description: String,
    val onCommands: List<String>,
    val offCommands: List<String>,
    val networkMaskCaptureCommand: String?,
    val isBuiltIn: Boolean
)

object BuiltInPresets {
    const val ID_STOCK_PIXEL = "builtin_stock_pixel"
    const val ID_SAMSUNG_ONE_UI = "builtin_samsung_one_ui"
    const val ID_XIAOMI_HYPEROS = "builtin_xiaomi_hyperos"
    const val ID_LEGACY = "builtin_legacy"
    const val ID_UNIVERSAL = "builtin_universal"
    const val ID_ONEPLUS = "builtin_oneplus"
    const val ID_ORIGINOS = "builtin_originos"
    const val MASK_PLACEHOLDER = "{{SAVED_MASK}}"
    const val LTE_ONLY_MASK = "01000001000000000000"
    const val MASK_CAPTURE_COMMAND = "cmd phone get-allowed-network-types-for-users -s 0"
    const val IMS_DISABLE_COMMAND = "cmd phone ims disable -s 0"
    const val IMS_ENABLE_COMMAND = "cmd phone ims enable -s 0"
    const val SAMSUNG_IMS_PACKAGE = "com.sec.imsservice"
    const val SAMSUNG_IMS_PACKAGE_NEW = "com.samsung.android.imsservice"
    const val QUALCOMM_IMS_PACKAGE = "org.codeaurora.ims"
    const val MEDIATEK_IMS_PACKAGE = "com.mediatek.ims"
    const val GOOGLE_IMS_PACKAGE = "com.google.android.ims"
    const val NETWORK_MODE_LTE_ONLY = "11"
    const val NETWORK_MODE_GLOBAL = "0"
    const val SLOT_0 = "-s 0"
    const val IGNORE_FAILURE_SUFFIX = " || true"
    const val GET_IMS_SERVICE_DEVICE_COMMAND = "cmd phone ims get-ims-service $SLOT_0 -d"
    const val GET_IMS_SERVICE_CARRIER_COMMAND = "cmd phone ims get-ims-service $SLOT_0 -c"

    private const val SET_ALLOWED_NETWORK_TYPES_COMMAND = "cmd phone set-allowed-network-types-for-users"
    private const val PM_DISABLE_USER_COMMAND = "pm disable-user --user 0"
    private const val PM_ENABLE_COMMAND = "pm enable"
    private const val PREFERRED_NETWORK_MODE_COMMAND = "settings put global preferred_network_mode"
    private const val MODE_SUFFIX_PRIMARY = ""
    private const val MODE_SUFFIX_SUBSCRIPTION_1 = "1"
    private const val MODE_SUFFIX_SUBSCRIPTION_2 = "2"
    private const val AIRPLANE_MODE_ENABLE_COMMAND = "cmd connectivity airplane-mode enable"
    private const val AIRPLANE_MODE_DISABLE_COMMAND = "cmd connectivity airplane-mode disable"

    private val universal = Preset(
        id = ID_UNIVERSAL,
        title = "Универсальный (автоопределение)",
        description = "Сам находит IMS-сервис устройства через cmd phone и отключает его. " +
            "Начните с этого пресета: он работает везде, где доступны команды телефонии AOSP.",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypesCommand(LTE_ONLY_MASK),
            disableImsServiceByDiscoveryCommand(GET_IMS_SERVICE_DEVICE_COMMAND),
            disableImsServiceByDiscoveryCommand(GET_IMS_SERVICE_CARRIER_COMMAND)
        ),
        offCommands = listOf(
            setAllowedNetworkTypesCommand(MASK_PLACEHOLDER),
            IMS_ENABLE_COMMAND,
            enableImsPackageCommand(QUALCOMM_IMS_PACKAGE),
            enableImsPackageCommand(MEDIATEK_IMS_PACKAGE),
            enableImsPackageCommand(SAMSUNG_IMS_PACKAGE),
            enableImsPackageCommand(GOOGLE_IMS_PACKAGE)
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true
    )

    private val stockPixel = Preset(
        id = ID_STOCK_PIXEL,
        title = "Stock / Pixel (Android 12+)",
        description = "Проверенная связка: отключение IMS и блокировка в LTE-only. " +
            "Звонящие слышат «абонент недоступен», мобильный интернет работает.",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypesCommand(LTE_ONLY_MASK)
        ),
        offCommands = listOf(
            setAllowedNetworkTypesCommand(MASK_PLACEHOLDER),
            IMS_ENABLE_COMMAND
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true
    )

    private val xiaomiHyperOs = Preset(
        id = ID_XIAOMI_HYPEROS,
        title = "Xiaomi MIUI / HyperOS",
        description = "Те же команды, что для стока: на HyperOS скрытое меню заблокировано, " +
            "но shell-команды телефонии работают.",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypesCommand(LTE_ONLY_MASK)
        ),
        offCommands = listOf(
            setAllowedNetworkTypesCommand(MASK_PLACEHOLDER),
            IMS_ENABLE_COMMAND
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true
    )

    private val samsungOneUi = Preset(
        id = ID_SAMSUNG_ONE_UI,
        title = "Samsung One UI",
        description = "Расширенная связка: выключение IMS на уровне телефонии, отключение " +
            "всех IMS-пакетов Samsung (старый и новый стек), LTE-only через маску сетей " +
            "и preferred_network_mode для всех подписок. " +
            "Если связка не сработает — переключитесь на пресет Stock. " +
            "После первого включения может потребоваться перезагрузка, чтобы модем снял VoLTE-регистрацию.",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypesCommand(LTE_ONLY_MASK),
            preferredNetworkModeCommand(MODE_SUFFIX_PRIMARY, NETWORK_MODE_LTE_ONLY),
            preferredNetworkModeCommand(MODE_SUFFIX_SUBSCRIPTION_1, NETWORK_MODE_LTE_ONLY),
            preferredNetworkModeCommand(MODE_SUFFIX_SUBSCRIPTION_2, NETWORK_MODE_LTE_ONLY),
            "$PM_DISABLE_USER_COMMAND $SAMSUNG_IMS_PACKAGE",
            "$PM_DISABLE_USER_COMMAND $SAMSUNG_IMS_PACKAGE_NEW$IGNORE_FAILURE_SUFFIX"
        ),
        offCommands = listOf(
            "$PM_ENABLE_COMMAND $SAMSUNG_IMS_PACKAGE",
            "$PM_ENABLE_COMMAND $SAMSUNG_IMS_PACKAGE_NEW$IGNORE_FAILURE_SUFFIX",
            IMS_ENABLE_COMMAND,
            preferredNetworkModeCommand(MODE_SUFFIX_PRIMARY, NETWORK_MODE_GLOBAL),
            preferredNetworkModeCommand(MODE_SUFFIX_SUBSCRIPTION_1, NETWORK_MODE_GLOBAL),
            preferredNetworkModeCommand(MODE_SUFFIX_SUBSCRIPTION_2, NETWORK_MODE_GLOBAL),
            setAllowedNetworkTypesCommand(MASK_PLACEHOLDER)
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true
    )

    private val onePlusOxygenOs = Preset(
        id = ID_ONEPLUS,
        title = "OnePlus (OxygenOS, OnePlus 13+)",
        description = "Проверено на OnePlus 13 (OxygenOS 15/16): команды AOSP работают. " +
            "Запасной вариант — отключение IMS-сервиса org.codeaurora.ims (Snapdragon) " +
            "или com.mediatek.ims (MediaTek).",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypesCommand(LTE_ONLY_MASK),
            disableImsPackageCommand(QUALCOMM_IMS_PACKAGE),
            disableImsPackageCommand(MEDIATEK_IMS_PACKAGE)
        ),
        offCommands = listOf(
            setAllowedNetworkTypesCommand(MASK_PLACEHOLDER),
            IMS_ENABLE_COMMAND,
            enableImsPackageCommand(QUALCOMM_IMS_PACKAGE),
            enableImsPackageCommand(MEDIATEK_IMS_PACKAGE)
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true
    )

    private val vivoOriginOs = Preset(
        id = ID_ORIGINOS,
        title = "vivo / iQOO (OriginOS / Funtouch)",
        description = "Те же команды AOSP: на OriginOS 6 публично не подтверждены — если не сработает, " +
            "попробуйте «Универсальный». IMS-пакеты: Dimensity → com.mediatek.ims, " +
            "Snapdragon → org.codeaurora.ims.",
        onCommands = listOf(
            IMS_DISABLE_COMMAND,
            setAllowedNetworkTypesCommand(LTE_ONLY_MASK),
            disableImsPackageCommand(QUALCOMM_IMS_PACKAGE),
            disableImsPackageCommand(MEDIATEK_IMS_PACKAGE)
        ),
        offCommands = listOf(
            setAllowedNetworkTypesCommand(MASK_PLACEHOLDER),
            IMS_ENABLE_COMMAND,
            enableImsPackageCommand(QUALCOMM_IMS_PACKAGE),
            enableImsPackageCommand(MEDIATEK_IMS_PACKAGE)
        ),
        networkMaskCaptureCommand = MASK_CAPTURE_COMMAND,
        isBuiltIn = true
    )

    private val legacy = Preset(
        id = ID_LEGACY,
        title = "Старый Android (9–11)",
        description = "Запасной вариант через settings global. " +
            "На части прошивок игнорируется (известно на Samsung S21+).",
        onCommands = listOf(
            preferredNetworkModeCommand(MODE_SUFFIX_PRIMARY, NETWORK_MODE_LTE_ONLY),
            preferredNetworkModeCommand(MODE_SUFFIX_SUBSCRIPTION_1, NETWORK_MODE_LTE_ONLY),
            preferredNetworkModeCommand(MODE_SUFFIX_SUBSCRIPTION_2, NETWORK_MODE_LTE_ONLY),
            AIRPLANE_MODE_ENABLE_COMMAND,
            AIRPLANE_MODE_DISABLE_COMMAND
        ),
        offCommands = listOf(
            preferredNetworkModeCommand(MODE_SUFFIX_PRIMARY, NETWORK_MODE_GLOBAL),
            preferredNetworkModeCommand(MODE_SUFFIX_SUBSCRIPTION_1, NETWORK_MODE_GLOBAL),
            preferredNetworkModeCommand(MODE_SUFFIX_SUBSCRIPTION_2, NETWORK_MODE_GLOBAL),
            AIRPLANE_MODE_ENABLE_COMMAND,
            AIRPLANE_MODE_DISABLE_COMMAND
        ),
        networkMaskCaptureCommand = null,
        isBuiltIn = true
    )

    val ALL: List<Preset> = listOf(
        universal,
        stockPixel,
        xiaomiHyperOs,
        samsungOneUi,
        onePlusOxygenOs,
        vivoOriginOs,
        legacy
    )
    val DEFAULT_ID: String = ID_UNIVERSAL

    private fun setAllowedNetworkTypesCommand(networkMask: String): String =
        "$SET_ALLOWED_NETWORK_TYPES_COMMAND $SLOT_0 $networkMask"

    private fun preferredNetworkModeCommand(modeSuffix: String, networkMode: String): String =
        "$PREFERRED_NETWORK_MODE_COMMAND$modeSuffix $networkMode"

    private fun disableImsServiceByDiscoveryCommand(getImsServiceCommand: String): String =
        "p=\"\$($getImsServiceCommand | head -n1 | tr -d '\\r')\"; " +
            "[ -n \"\$p\" ] && [ \"\$p\" != \"null\" ] && " +
            "$PM_DISABLE_USER_COMMAND \"\$p\"$IGNORE_FAILURE_SUFFIX"

    private fun disableImsPackageCommand(imsPackage: String): String =
        "$PM_DISABLE_USER_COMMAND $imsPackage$IGNORE_FAILURE_SUFFIX"

    private fun enableImsPackageCommand(imsPackage: String): String =
        "$PM_ENABLE_COMMAND $imsPackage$IGNORE_FAILURE_SUFFIX"
}
