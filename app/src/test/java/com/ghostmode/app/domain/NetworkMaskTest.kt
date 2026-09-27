package com.ghostmode.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkMaskTest {

    @Test
    fun parse_binaryOutput() {
        assertEquals("11001111101111111111", NetworkMask.parse("11001111101111111111\n"))
    }

    @Test
    fun parse_networkTypeNames() {
        assertEquals(NetworkMask.LTE_ONLY, NetworkMask.parse("LTE|LTE_CA"))
    }

    @Test
    fun parse_errorOutput_returnsNull() {
        assertNull(NetworkMask.parse("Exception: something failed"))
    }

    @Test
    fun blocksLegacyVoice() {
        assertTrue(NetworkMask.blocksLegacyVoice(NetworkMask.LTE_ONLY))
        assertTrue(NetworkMask.blocksLegacyVoice(NetworkMask.fromTypes(setOf(13, 19, 20))))
        assertFalse(NetworkMask.blocksLegacyVoice(NetworkMask.FALLBACK_ALL))
        assertFalse(NetworkMask.blocksLegacyVoice("0"))
    }

    @Test
    fun fromTypes_matchesShellFormat() {
        assertEquals(NetworkMask.LTE_ONLY, NetworkMask.fromTypes(setOf(13, 19)))
        assertEquals(setOf(13, 19), NetworkMask.toTypes(NetworkMask.LTE_ONLY))
    }
}
