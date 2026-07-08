package com.styleconverter.test.style.layout.flexbox

// Phase 7b flexbox applier tests — exercise the LayoutConfig → FlexDecision
// mapping. Separate from extractor tests so a broken keyword parse doesn't
// cascade into a broken container selection assertion.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import com.styleconverter.test.style.layout.AlignmentKeyword
import com.styleconverter.test.style.layout.DisplayKind
import com.styleconverter.test.style.layout.FlexDirection
import com.styleconverter.test.style.layout.FlexWrap
import com.styleconverter.test.style.layout.LayoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexboxApplierTest {

    // --- display gating -----------------------------------------------------

    @Test fun `non-flex display returns null so legacy path handles it`() {
        val cfg = LayoutConfig(display = DisplayKind.Block)
        assertNull(FlexboxApplier.decide(cfg))
    }

    @Test fun `null display returns null`() {
        assertNull(FlexboxApplier.decide(LayoutConfig.Empty))
    }

    @Test fun `display none returns None-kind decision`() {
        val d = FlexboxApplier.decide(LayoutConfig(display = DisplayKind.None))
        assertNotNull(d)
        assertEquals(FlexContainerKind.None, d!!.kind)
    }

    @Test fun `display flex default row nowrap picks Row kind`() {
        val d = FlexboxApplier.decide(LayoutConfig(display = DisplayKind.Flex))!!
        assertEquals(FlexContainerKind.Row, d.kind)
        assertFalse(d.reverse)
    }

    @Test fun `inline-flex also picks Row`() {
        val d = FlexboxApplier.decide(LayoutConfig(display = DisplayKind.InlineFlex))!!
        assertEquals(FlexContainerKind.Row, d.kind)
    }

    // --- kind selection -----------------------------------------------------

    @Test fun `flex + column picks Column`() {
        val d = FlexboxApplier.decide(LayoutConfig(
            display = DisplayKind.Flex,
            flexDirection = FlexDirection.Column
        ))!!
        assertEquals(FlexContainerKind.Column, d.kind)
    }

    @Test fun `flex + wrap picks FlowRow`() {
        val d = FlexboxApplier.decide(LayoutConfig(
            display = DisplayKind.Flex,
            flexWrap = FlexWrap.Wrap
        ))!!
        assertEquals(FlexContainerKind.FlowRow, d.kind)
    }

    @Test fun `flex + column + wrap picks FlowColumn`() {
        val d = FlexboxApplier.decide(LayoutConfig(
            display = DisplayKind.Flex,
            flexDirection = FlexDirection.Column,
            flexWrap = FlexWrap.Wrap
        ))!!
        assertEquals(FlexContainerKind.FlowColumn, d.kind)
    }

    @Test fun `row-reverse flags reverse true`() {
        val d = FlexboxApplier.decide(LayoutConfig(
            display = DisplayKind.Flex,
            flexDirection = FlexDirection.RowReverse
        ))!!
        assertEquals(FlexContainerKind.Row, d.kind)
        assertTrue(d.reverse)
    }

    @Test fun `wrap-reverse still uses FlowRow`() {
        val d = FlexboxApplier.decide(LayoutConfig(
            display = DisplayKind.Flex,
            flexWrap = FlexWrap.WrapReverse
        ))!!
        assertEquals(FlexContainerKind.FlowRow, d.kind)
    }

    // --- JustifyContent → Arrangement mapping (all 6) ----------------------

    @Test fun `justify flex-start maps to Arrangement Start`() {
        assertEquals(Arrangement.Start, FlexboxApplier.toHorizontalArrangement(AlignmentKeyword.FlexStart))
    }

    @Test fun `justify flex-end maps to Arrangement End`() {
        assertEquals(Arrangement.End, FlexboxApplier.toHorizontalArrangement(AlignmentKeyword.FlexEnd))
    }

    @Test fun `justify center maps to Arrangement Center`() {
        assertEquals(Arrangement.Center, FlexboxApplier.toHorizontalArrangement(AlignmentKeyword.Center))
    }

    @Test fun `justify space-between`() {
        assertEquals(Arrangement.SpaceBetween, FlexboxApplier.toHorizontalArrangement(AlignmentKeyword.SpaceBetween))
    }

    @Test fun `justify space-around`() {
        assertEquals(Arrangement.SpaceAround, FlexboxApplier.toHorizontalArrangement(AlignmentKeyword.SpaceAround))
    }

    @Test fun `justify space-evenly`() {
        assertEquals(Arrangement.SpaceEvenly, FlexboxApplier.toHorizontalArrangement(AlignmentKeyword.SpaceEvenly))
    }

    @Test fun `justify vertical center maps to Arrangement Center`() {
        assertEquals(Arrangement.Center, FlexboxApplier.toVerticalArrangement(AlignmentKeyword.Center))
    }

    // --- AlignItems → Alignment mapping (all 5) ----------------------------

    @Test fun `align-items flex-start vertical maps to Top`() {
        assertEquals(Alignment.Top, FlexboxApplier.toVerticalAlignment(AlignmentKeyword.FlexStart))
    }

    @Test fun `align-items flex-end vertical maps to Bottom`() {
        assertEquals(Alignment.Bottom, FlexboxApplier.toVerticalAlignment(AlignmentKeyword.FlexEnd))
    }

    @Test fun `align-items center vertical maps to CenterVertically`() {
        assertEquals(Alignment.CenterVertically, FlexboxApplier.toVerticalAlignment(AlignmentKeyword.Center))
    }

    @Test fun `align-items stretch vertical approximated to CenterVertically`() {
        // See applier comment — Compose has no vertical Stretch primitive.
        assertEquals(Alignment.CenterVertically, FlexboxApplier.toVerticalAlignment(AlignmentKeyword.Stretch))
    }

    @Test fun `align-items center horizontal maps to CenterHorizontally`() {
        assertEquals(Alignment.CenterHorizontally, FlexboxApplier.toHorizontalAlignment(AlignmentKeyword.Center))
    }

    // --- Integration scenario ---------------------------------------------

    @Test fun `integration flex row space-between center alignItems`() {
        // display: flex; flex-direction: row; justify-content: space-between;
        // align-items: center  -> the canonical "navbar" shape.
        val d = FlexboxApplier.decide(LayoutConfig(
            display = DisplayKind.Flex,
            flexDirection = FlexDirection.Row,
            justifyContent = AlignmentKeyword.SpaceBetween,
            alignItems = AlignmentKeyword.Center
        ))!!
        assertEquals(FlexContainerKind.Row, d.kind)
        assertEquals(Arrangement.SpaceBetween, d.horizontalArrangement)
        assertEquals(Alignment.CenterVertically, d.verticalAlignment)
    }
}
