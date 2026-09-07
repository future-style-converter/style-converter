package com.styleconverter.runtime.layout.flexbox

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer.AlignSelf
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 47 (lane Z3) — pins the css-flexbox-1 §8.3 cross-axis stretch
 * decision for the legacy flex loops.
 *
 * The fillMax* modifiers themselves need a Compose layout pass (Robolectric,
 * which this suite deliberately does not use), so the WHOLE decision lives
 * in [FlexCrossStretch] and this file pins it on the JVM — the same
 * "extract the decision, pin it on the JVM" shape as FlexAutoMinSizeTest.
 */
class FlexCrossStretchTest {

    // Convenience: the all-gates-open composed call, overridable per test.
    private fun decide(
        // Retro R2 seam patch: the capture mode is no longer an input; the
        // parameter is kept on this helper so the existing pins read as-is.
        @Suppress("UNUSED_PARAMETER") composedWpt: Boolean = true,
        alignSelf: AlignSelf = AlignSelf.AUTO,
        rowAxis: Boolean = true,
        containerItemsStretch: Boolean = true,
        containerCrossDefinite: Boolean = true,
        childCrossDefinite: Boolean = false,
        childIsOutOfFlow: Boolean = false,
        childGeneratesBox: Boolean = true,
    ) = FlexCrossStretch.effectiveStretch(
        alignSelf, rowAxis, containerItemsStretch,
        containerCrossDefinite, childCrossDefinite, childIsOutOfFlow,
        childGeneratesBox,
    )

    // ── The defect this lane fixes ─────────────────────────────────────

    @Test
    fun `calc-size-flex row shape - auto item under default align-items stretches`() {
        // calc-size-flex-001..003 / 009 + align-self-011/012: no align-self,
        // no align-items (initial `normal` behaves as stretch), container
        // height definite, item height auto → the item fills the cross axis.
        assertTrue(decide(alignSelf = AlignSelf.AUTO, rowAxis = true))
    }

    @Test
    fun `calc-size-flex column shape - auto item stretches on the width axis`() {
        // calc-size-flex-004..006: `flex-direction: column`, width 100
        // definite, item width auto → fillMaxWidth.
        assertTrue(decide(alignSelf = AlignSelf.AUTO, rowAxis = false))
    }

    // ── Frozen legacy behaviour (composedWpt = false) ──────────────────

    @Test
    fun `dark stage - explicit row stretch keeps its historical fill`() {
        // The pre-wave-47 row predicate was exactly
        // `alignSelf == STRETCH && !childCrossDefinite && !childIsOutOfFlow`
        // — no composed gate, no container-definite gate. Byte-stability.
        assertTrue(decide(composedWpt = false, alignSelf = AlignSelf.STRETCH,
            rowAxis = true, containerCrossDefinite = false, containerItemsStretch = false))
    }

    // ── Retro R2 (A11#12): the capture mode no longer gates the spec arms ─

    @Test
    fun `dark stage - auto stretches exactly like composed capture`() {
        // css-flexbox-1 §8.3 does not know what a capture mode is. Wave 47's
        // composed-only gate made Android render FC_AlignSelf / N3 /
        // AI_Stretch items content-sized on the dark stage (A11#12); both
        // modes now return the same decision for the same inputs.
        assertTrue(decide(composedWpt = false, alignSelf = AlignSelf.AUTO))
        assertEquals(decide(composedWpt = true), decide(composedWpt = false))
    }

    @Test
    fun `dark stage - explicit column stretch fills the line cross size`() {
        // §9.4 step 11: the audit's FC_AlignSelf `d` row — `align-self:
        // stretch`, no width, in a 200px-wide column container — must fill
        // (iOS 188 wide, CSS-correct) instead of hugging (Android/web 48).
        assertTrue(decide(composedWpt = false, alignSelf = AlignSelf.STRETCH, rowAxis = false))
    }

    @Test
    fun `FC_AlignSelf verbatim IR - only d stretches, a b c are positional`() {
        // fixtures/fidelity/trees/flex-column.json → converter IR (retro R2
        // scratch run): container `AlignItems: "CENTER"` → auto items do NOT
        // stretch; `d` carries `AlignSelf: "STRETCH"` and no Width.
        val container = listOf(IRProperty("AlignItems", JsonPrimitive("CENTER")),
            IRProperty("FlexDirection", JsonPrimitive("COLUMN")))
        val itemsStretch = FlexCrossStretch.containerAlignItemsStretches(container)
        assertFalse(itemsStretch)
        // a/b/c: explicit positional align-self + a definite 80px width.
        for (a in listOf(AlignSelf.FLEX_START, AlignSelf.CENTER, AlignSelf.FLEX_END)) {
            assertFalse(decide(composedWpt = false, alignSelf = a, rowAxis = false,
                containerItemsStretch = itemsStretch, childCrossDefinite = true))
        }
        // d: stretch, width auto → fills the 200px (minus padding) line.
        assertTrue(decide(composedWpt = false, alignSelf = AlignSelf.STRETCH, rowAxis = false,
            containerItemsStretch = itemsStretch, childCrossDefinite = false))
    }

    @Test
    fun `N3_ColumnOfRows and AI_Stretch verbatim IR - auto items stretch on both axes`() {
        // nested-3level N3_ColumnOfRows: Width 280, FlexDirection COLUMN, no
        // AlignItems → initial `normal` = stretch; row1/row2 have no Width.
        assertTrue(FlexCrossStretch.containerAlignItemsStretches(
            listOf(IRProperty("FlexDirection", JsonPrimitive("COLUMN")))))
        assertTrue(decide(composedWpt = false, alignSelf = AlignSelf.AUTO, rowAxis = false))
        // flex-align-items AI_Stretch: Width 300, Height 100, `AlignItems:
        // "STRETCH"`, children width 50 and NO height → fill the row height.
        assertTrue(FlexCrossStretch.containerAlignItemsStretches(
            listOf(IRProperty("AlignItems", JsonPrimitive("STRETCH")))))
        assertTrue(decide(composedWpt = false, alignSelf = AlignSelf.AUTO, rowAxis = true))
    }

    // ── Spec gates on the new arms ─────────────────────────────────────

    @Test
    fun `definite child cross size degrades stretch to flex-start`() {
        // css-flexbox-1 §8.3: stretch only applies to an auto cross size —
        // both the auto arm and the frozen explicit arm honour it.
        assertFalse(decide(childCrossDefinite = true))
        assertFalse(decide(alignSelf = AlignSelf.STRETCH, childCrossDefinite = true))
    }

    @Test
    fun `out-of-flow children are not flex items`() {
        // css-flexbox-1 §4.1: abspos/fixed children take the static-position
        // machinery, never a stretch.
        assertFalse(decide(childIsOutOfFlow = true))
        assertFalse(decide(alignSelf = AlignSelf.STRETCH, childIsOutOfFlow = true))
    }

    @Test
    fun `boxless display-contents children are excluded from the new arm`() {
        // css-display-3 §2.5: `display: contents` generates no box — its
        // CHILDREN are the items. Filling the wrapper would stretch a
        // non-box (the display-contents-dynamic-flex-001 passing cells).
        assertFalse(decide(childGeneratesBox = false))
    }

    @Test
    fun `auto container cross size stands the new arm down`() {
        // §9.4 step 8 grows lines into a DEFINITE container cross size; a
        // fillMax* against an indefinite one would fill the canvas
        // remainder (the FlexboxApplier weight-blowout shape).
        assertFalse(decide(containerCrossDefinite = false))
        assertFalse(decide(alignSelf = AlignSelf.STRETCH, rowAxis = false,
            containerCrossDefinite = false))
    }

    @Test
    fun `non-stretch align-items keeps auto items positional`() {
        // `align-self: auto` resolves to the container's align-items
        // (css-align-3 §6.2) — center/start/end are positional, no fill.
        assertFalse(decide(containerItemsStretch = false))
    }

    @Test
    fun `explicit column stretch fills in composed capture`() {
        // An explicit `align-self: stretch` column item fills its width
        // (§9.4 step 11) — composed capture was the first mode to get it.
        assertTrue(decide(alignSelf = AlignSelf.STRETCH, rowAxis = false))
    }

    @Test
    fun `positional align-self values never stretch`() {
        // start/end/center/baseline are positional per §8.3 — the `else`
        // arms in both loops rely on `stretches` being false for BASELINE.
        for (a in listOf(AlignSelf.FLEX_START, AlignSelf.FLEX_END,
            AlignSelf.CENTER, AlignSelf.BASELINE)) {
            assertFalse(decide(alignSelf = a))
        }
    }

    // ── containerAlignItemsStretches keyword table ─────────────────────

    @Test
    fun `absent align-items is stretch`() {
        // CSS initial `normal` behaves as stretch in a flex container —
        // the exact shape of all seven calc-size-flex containers.
        assertTrue(FlexCrossStretch.containerAlignItemsStretches(emptyList()))
    }

    @Test
    fun `normal and stretch keywords fold to stretch like the display fold`() {
        for (kw in listOf("NORMAL", "STRETCH", "normal", "stretch")) {
            assertTrue(kw, FlexCrossStretch.containerAlignItemsStretches(
                listOf(IRProperty("AlignItems", JsonPrimitive(kw)))))
        }
    }

    @Test
    fun `positional align-items keywords are not stretch`() {
        for (kw in listOf("CENTER", "FLEX_START", "FLEX-START", "START",
            "FLEX_END", "FLEX-END", "END", "BASELINE", "center", "flex-end")) {
            assertFalse(kw, FlexCrossStretch.containerAlignItemsStretches(
                listOf(IRProperty("AlignItems", JsonPrimitive(kw)))))
        }
    }

    // ── childGeneratesBox display table ────────────────────────────────

    @Test
    fun `contents and none generate no box`() {
        for (kw in listOf("CONTENTS", "NONE", "contents", "none")) {
            assertFalse(kw, FlexCrossStretch.childGeneratesBox(
                listOf(IRProperty("Display", JsonPrimitive(kw)))))
        }
    }

    @Test
    fun `ordinary and absent display values generate a box`() {
        // Blockified per css-display-3 §2.7 — all real flex items.
        for (kw in listOf("BLOCK", "INLINE", "FLEX", "GRID")) {
            assertTrue(kw, FlexCrossStretch.childGeneratesBox(
                listOf(IRProperty("Display", JsonPrimitive(kw)))))
        }
        assertTrue(FlexCrossStretch.childGeneratesBox(emptyList()))
    }
}
