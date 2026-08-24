package com.styleconverter.runtime.layout.flexbox

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer.AlignSelf
import kotlinx.serialization.json.JsonPrimitive
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
        composedWpt: Boolean = true,
        alignSelf: AlignSelf = AlignSelf.AUTO,
        rowAxis: Boolean = true,
        containerItemsStretch: Boolean = true,
        containerCrossDefinite: Boolean = true,
        childCrossDefinite: Boolean = false,
        childIsOutOfFlow: Boolean = false,
        childGeneratesBox: Boolean = true,
    ) = FlexCrossStretch.effectiveStretch(
        composedWpt, alignSelf, rowAxis, containerItemsStretch,
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

    @Test
    fun `dark stage - auto never stretches (the frozen no-op)`() {
        // Outside composed capture the fit-content calibration stands: an
        // `auto` item keeps the legacy hug regardless of the other gates.
        assertFalse(decide(composedWpt = false, alignSelf = AlignSelf.AUTO))
    }

    @Test
    fun `dark stage - explicit column stretch keeps Alignment-Start, no fill`() {
        // The column loop NEVER filled before wave 47 (FC_AlignSelf pixel
        // calibration) — composed=false must keep that exactly.
        assertFalse(decide(composedWpt = false, alignSelf = AlignSelf.STRETCH, rowAxis = false))
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
        // (css-align-3 §6.4) — center/start/end are positional, no fill.
        assertFalse(decide(containerItemsStretch = false))
    }

    @Test
    fun `explicit column stretch fills in composed capture`() {
        // The composed WPT page has no fit-content wrapper, so an explicit
        // `align-self: stretch` column item genuinely fills its width.
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
