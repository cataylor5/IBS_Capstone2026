package io.github.cataylor5.ibs

import io.github.cataylor5.ibs.ui.theme.IbsBrand
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandContrastTest {
    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
    private fun contrast(a: Int, b: Int): Double {
        val light = maxOf(luminance(a), luminance(b))
        val dark = minOf(luminance(a), luminance(b))
        return (light + 0.05) / (dark + 0.05)
    }
    @Test fun whiteNavigationTextMeetsNormalTextContrastOnBothDayAndNightBannerColors() {
        assertTrue(contrast(-1, IbsBrand.CobaltArgb) >= 4.5)
        assertTrue(contrast(-1, IbsBrand.NavyArgb) >= 4.5)
    }
    @Test fun selectedChipsAndLightBackgroundKeepNavyTextLegible() {
        assertTrue(contrast(IbsBrand.NavyArgb, IbsBrand.PaleAquaArgb) >= 7.0)
        assertTrue(contrast(IbsBrand.NavyArgb, IbsBrand.BackgroundArgb) >= 7.0)
    }
}
