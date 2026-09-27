package com.ghostmode.app.domain

/**
 * Helpers for the allowed-network-types bitmask used by
 * `cmd phone get/set-allowed-network-types-for-users`.
 *
 * The shell command accepts a binary string where bit `n - 1` enables network type `n`
 * (see `TelephonyManager.NETWORK_TYPE_*`). Depending on the Android build the getter prints
 * either the same binary string or a `|`-separated list of network type names.
 */
object NetworkMask {

    const val LTE_ONLY = "01000001000000000000"

    /** All standard radio technologies; used only when the original mask cannot be read. */
    const val FALLBACK_ALL = "11001111101111111111"

    private const val MASK_LENGTH = 20
    private val BINARY_PATTERN = Regex("(?<![0-9])[01]{16,32}(?![0-9])")

    private val NETWORK_TYPE_BY_NAME: Map<String, Int> = mapOf(
        "GPRS" to 1,
        "EDGE" to 2,
        "UMTS" to 3,
        "CDMA" to 4,
        "CDMA - EvDo rev. 0" to 5,
        "CDMA - EvDo rev. A" to 6,
        "CDMA - 1xRTT" to 7,
        "HSDPA" to 8,
        "HSUPA" to 9,
        "HSPA" to 10,
        "iDEN" to 11,
        "CDMA - EvDo rev. B" to 12,
        "LTE" to 13,
        "CDMA - eHRPD" to 14,
        "HSPA+" to 15,
        "GSM" to 16,
        "TD_SCDMA" to 17,
        "IWLAN" to 18,
        "LTE_CA" to 19,
        "NR" to 20
    ).mapKeys { (name, _) -> name.lowercase() }

    /** Network types that allow circuit-switched (2G/3G) voice, i.e. CSFB targets. */
    private val LEGACY_TYPES = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17)

    /**
     * Extracts a binary mask from command output. Returns `null` if nothing recognisable was
     * printed (for example an error message).
     */
    fun parse(output: String): String? {
        BINARY_PATTERN.findAll(output).lastOrNull()?.let { return it.value }
        val types = output.lineSequence()
            .flatMap { line -> line.split('|', ',').asSequence() }
            .mapNotNull { token -> NETWORK_TYPE_BY_NAME[token.trim().lowercase()] }
            .toSet()
        return if (types.isEmpty()) null else fromTypes(types)
    }

    fun fromTypes(types: Set<Int>): String {
        val value = types.fold(0L) { acc, type -> acc or (1L shl (type - 1)) }
        return java.lang.Long.toBinaryString(value).padStart(MASK_LENGTH, '0')
    }

    fun toTypes(mask: String): Set<Int> {
        val value = mask.toLongOrNull(radix = 2) ?: return emptySet()
        return (1..63).filter { type -> value and (1L shl (type - 1)) != 0L }.toSet()
    }

    /** `true` when the mask leaves no 2G/3G technology the modem could fall back to for calls. */
    fun blocksLegacyVoice(mask: String): Boolean {
        val types = toTypes(mask)
        return types.isNotEmpty() && types.none { it in LEGACY_TYPES }
    }
}
