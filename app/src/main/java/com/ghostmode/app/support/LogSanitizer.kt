package com.ghostmode.app.support

/**
 * Removes personal identifiers from text before it enters the command log, so that users can
 * paste the log into a public bug report without reviewing it first.
 *
 * The first line of defence is that diagnostic commands only print a whitelist of values; this
 * filter is the safety net for anything a device prints on its own (error messages, stack
 * traces, vendor tools).
 */
object LogSanitizer {

    private val URI = Regex("\\b(?:sips?|tel):[^\\s,;>\"]+", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+")
    private val MAC = Regex("(?<![0-9A-Fa-f:-])(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}(?![0-9A-Fa-f:-])")
    private val IPV6_CANDIDATE = Regex("(?<![\\w:.])[0-9A-Fa-f:]*:[0-9A-Fa-f:]*:[0-9A-Fa-f:]*(?![\\w:.])")
    private val IPV4 = Regex("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])")

    /** "+7 (999) 123-45-67": digits split by separators. */
    private val GROUPED_NUMBER = Regex("(?<![\\w.+])\\+?\\d[\\d ()-]{7,}\\d(?![\\w.])")

    /** Phone numbers, IMEI, IMSI, ICCID (which may end with a hex `f` filler). */
    private val LONG_NUMBER = Regex("(?<![\\w.])\\+?\\d{7,}[Ff]?(?![\\w.])")
    private val NETWORK_MASK = Regex("[01]{16,}")

    private const val MIN_GROUPED_DIGITS = 9
    private const val MIN_IPV6_GROUPS = 4

    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(URI, "<uri>")
            .replace(EMAIL, "<email>")
            .replace(MAC, "<mac>")
            .replace(IPV6_CANDIDATE) { match -> if (isIpv6(match.value)) "<ip>" else match.value }
            .replace(IPV4, "<ip>")
            .replace(GROUPED_NUMBER) { match ->
                val isNumber = match.value.count(Char::isDigit) >= MIN_GROUPED_DIGITS
                if (isNumber && !NETWORK_MASK.containsMatchIn(match.value)) "<number>" else match.value
            }
            .replace(LONG_NUMBER) { match ->
                // Allowed-network-type masks are the whole point of the log.
                if (NETWORK_MASK.matches(match.value)) match.value else "<number>"
            }
    }

    /** Clock times such as 12:34:56 also consist of hex digits and colons. */
    private fun isIpv6(value: String): Boolean =
        value.length > 2 && ("::" in value || value.split(':').size >= MIN_IPV6_GROUPS)
}
