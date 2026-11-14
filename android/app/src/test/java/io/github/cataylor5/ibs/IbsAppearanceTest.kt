package io.github.cataylor5.ibs

import io.github.cataylor5.ibs.ui.theme.*
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class IbsAppearanceTest {
    @Test fun homeUsesSystemChangesWhenNoMapIsActive() {
        val day = IbsModeState().systemChanged(false)
        assertEquals(false, day.night)
        assertEquals(true, day.systemChanged(true).night)
        assertEquals(false, day.systemChanged(true).systemChanged(false).night)
    }
    @Test fun sdkEffectiveModeWinsOverSystemWhileMapIsActive() {
        val state = IbsModeState().systemChanged(false).mapOpened().mapChanged(true)
        assertEquals(true, state.night)
        assertEquals(true, state.systemChanged(true).systemChanged(false).night)
        assertEquals(false, state.mapChanged(false).night)
    }
    @Test fun returningHomeKeepsLastSdkModeUntilARealSystemChange() {
        val returned = IbsModeState().systemChanged(false).mapOpened().mapChanged(true).mapClosed()
        assertEquals(true, returned.systemChanged(false).night)
        assertEquals(true, returned.systemChanged(true).night)
        assertEquals(false, returned.systemChanged(true).systemChanged(false).night)
    }
    @Test fun persistedModeIsNotLostOnFirstHomeComposition() {
        val restored = IbsModeState(night = true).systemChanged(false)
        assertEquals(true, restored.night)
    }
    @Test fun recreatingMapsDoesNotReleaseTheNewMapsAuthority() {
        val state = IbsModeState().systemChanged(false).mapOpened().mapOpened().mapChanged(true).mapClosed()
        assertEquals(1, state.mapCount)
        assertEquals(true, state.systemChanged(true).systemChanged(false).night)
        assertEquals(0, state.mapClosed().mapClosed().mapCount)
    }
    @Test fun bothPalettesKeepSurfaceTextAndSelectedLabelsReadable() {
        for (night in listOf(false, true)) {
            val p = ibsSurfacePalette(night)
            assertTrue(contrast(p.text, p.background) >= 4.5)
            assertTrue(contrast(p.text, p.surface) >= 4.5)
            assertTrue(contrast(p.selectedText, p.selected) >= 4.5)
            assertTrue(contrast(p.actionText, p.surface) >= 4.5)
        }
        assertNotEquals(-1, ibsSurfacePalette(true).surface)
        assertEquals(-1, ibsSurfacePalette(false).surface)
    }
    private fun contrast(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val v = ((color ushr shift) and 255) / 255.0
                return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        return (maxOf(luminance(a), luminance(b)) + 0.05) / (minOf(luminance(a), luminance(b)) + 0.05)
    }
}
