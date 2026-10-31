package com.ghostmode.app.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogSanitizerTest {

    private fun clean(text: String) = LogSanitizer.sanitize(text)

    @Test
    fun removesPhoneNumbers() {
        assertEquals("from <number>", clean("from +79991234567"))
        assertEquals("from <number>", clean("from +7 999 123-45-67"))
        assertEquals("from <number>", clean("from 89991234567"))
    }

    @Test
    fun removesDeviceAndSimIdentifiers() {
        assertEquals("imei=<number>", clean("imei=356938035643809"))
        assertEquals("imsi <number>", clean("imsi 250011234567890"))
        assertEquals("iccid <number>", clean("iccid 8970101234567890123f"))
    }

    @Test
    fun removesNetworkAddressesAndAccounts() {
        assertEquals("addr <ip> up", clean("addr 192.168.1.10 up"))
        assertEquals("addr <ip> up", clean("addr 2a00:1450:4001:81a::200e up"))
        assertEquals("wlan0 <mac>", clean("wlan0 a4:5e:60:c2:11:7f"))
        assertEquals("user <email>", clean("user someone@example.com"))
        assertEquals("from <uri>", clean("from sip:+79991234567@ims.mnc001.mcc250.3gppnetwork.org"))
        assertEquals("from <uri>", clean("from tel:+79991234567"))
    }

    @Test
    fun keepsWhatDiagnosticsNeed() {
        val kept = listOf(
            "set-allowed-network-types-for-users -s 0 11001101001110000111",
            "cmd phone set-allowed-network-types-for-users -s 1 01000001000000000000",
            "settings put global preferred_network_mode 26,26",
            "gsm.sim.operator.numeric=405857",
            "ro.build.version.release=16",
            "versionName=1.2.3.4567",
            "com.sec.imsservice: 4821",
            "started at 12:34:56"
        )
        kept.forEach { assertEquals(it, clean(it)) }
    }

    @Test
    fun leavesNoTraceInsideStackTraces() {
        val line = "Exception for +79991234567: java.lang.SecurityException from pid=19958, uid=2000"
        assertFalse("79991234567" in clean(line))
        assertEquals("Exception for <number>: java.lang.SecurityException from pid=19958, uid=2000", clean(line))
    }
}
