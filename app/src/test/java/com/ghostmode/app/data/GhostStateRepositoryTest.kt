package com.ghostmode.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GhostStateRepositoryTest {

    private lateinit var repository: GhostStateRepository

    @Before
    fun setUp() {
        repository = GhostStateRepository(null)
    }

    @Test
    fun setIsOn_updatesIsOnAndTimestamp() {
        assertFalse(repository.isOn.value)
        assertEquals(0L, repository.isOnTimestampMs.value)

        repository.setIsOn(true)
        assertTrue(repository.isOn.value)
        assertTrue(repository.isOnTimestampMs.value > 0L)

        repository.setIsOn(false)
        assertFalse(repository.isOn.value)
        assertEquals(0L, repository.isOnTimestampMs.value)
    }

    @Test
    fun setNotificationEnabled_updatesFlow() {
        assertFalse(repository.notificationEnabled.value)
        repository.setNotificationEnabled(true)
        assertTrue(repository.notificationEnabled.value)
    }

    @Test
    fun setSavedNetworkMask_updatesFlow() {
        assertEquals(null, repository.savedNetworkMask.value)
        repository.setSavedNetworkMask("11001111101111111111")
        assertEquals("11001111101111111111", repository.savedNetworkMask.value)
    }
}
