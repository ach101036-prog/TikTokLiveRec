package com.example

import com.example.data.network.TikTokLiveChecker
import com.example.service.RecordingCoordinator
import com.example.service.RecordingPhase
import com.example.ui.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVaultUnitTests {

    @Test
    fun normalizeUsername_handlesHandlesAndUrls() {
        assertEquals("creator_1", TikTokLiveChecker.normalizeUsername("@creator_1"))
        assertEquals("creator.two", TikTokLiveChecker.normalizeUsername("  creator.two "))
        assertEquals(
            "streamstar",
            TikTokLiveChecker.normalizeUsername("https://www.tiktok.com/@streamstar/live?ref=share")
        )
        assertEquals("", TikTokLiveChecker.normalizeUsername("   "))
    }

    @Test
    fun formatters_formatDurationBytesAndViewers() {
        assertEquals("00:00:00", Formatters.formatDuration(0L))
        assertEquals("01:02:05", Formatters.formatDuration((3600 + 120 + 5) * 1000L))
        assertEquals("512 B", Formatters.formatBytes(512L))
        assertEquals("1.5 MB", Formatters.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("950", Formatters.formatViewerCount(950L))
        assertEquals("2.5K", Formatters.formatViewerCount(2500L))
    }

    @Test
    fun recordingCoordinator_updatesTimerAndStopFlag() {
        val coordinator = RecordingCoordinator()
        assertFalse(coordinator.state.value.isBusy)

        coordinator.updateTimer(5000L)
        assertEquals(5000L, coordinator.state.value.elapsedMs)

        coordinator.markFailed("Stream ended")
        assertTrue(coordinator.state.value.phase is RecordingPhase.Failed)

        coordinator.clearBannerState()
        assertTrue(coordinator.state.value.phase is RecordingPhase.Idle)
    }
}
