package io.github.cataylor5.ibs

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class DestinationDropdownPositionTest {
    private val provider = AboveDestinationField()
    @Test fun suggestionsOpenAboveTheFooterRatherThanOverTheButtonOrKeyboard() {
        val anchor = IntRect(16, 400, 384, 464)
        val position = provider.calculatePosition(anchor, IntSize(400, 500),
            LayoutDirection.Ltr, IntSize(368, 220))
        assertEquals(IntOffset(16, 176), position)
        assertTrue(position.y + 220 < anchor.top)
    }

    @Test fun rtlAlignsToTheFieldsRightEdge() {
        assertEquals(IntOffset(184, 176), provider.calculatePosition(IntRect(16, 400, 384, 464),
            IntSize(400, 500), LayoutDirection.Rtl, IntSize(200, 220)))
    }

    @Test fun dropdownRemainsInsideWindowForNarrowOrResizedWindows() {
        val position = provider.calculatePosition(IntRect(180, 300, 380, 360),
            IntSize(300, 450), LayoutDirection.Ltr, IntSize(200, 180))
        assertEquals(IntOffset(100, 116), position)
    }
}
