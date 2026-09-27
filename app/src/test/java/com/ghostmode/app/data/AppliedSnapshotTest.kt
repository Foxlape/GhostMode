package com.ghostmode.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppliedSnapshotTest {

    @Test
    fun jsonRoundTrip_keepsNullOriginals() {
        val snapshot = AppliedSnapshot(
            presetId = "custom_1",
            offCommands = listOf("a", "b || true"),
            slots = listOf(0, 1),
            settingsOriginals = mapOf("global/x" to "1", "secure/y" to null),
            disabledPackages = listOf("p.q"),
            bootCount = 12,
            appliedAtMs = 99L
        )

        assertEquals(snapshot, AppliedSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun fromJson_toleratesGarbage() {
        assertNull(AppliedSnapshot.fromJson("{not json"))
        assertNull(AppliedSnapshot.fromJson(null))
    }
}
