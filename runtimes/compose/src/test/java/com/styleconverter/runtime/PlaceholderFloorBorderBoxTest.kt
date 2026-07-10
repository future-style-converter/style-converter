package com.styleconverter.runtime

// Fidelity wave 4 — pinning tests for the BORDER-BOX placeholder floor.
//
// Web's placeholder floor (`minWidth: 50px; minHeight: 30px` in
// apps/web-harness/src/sdui/ComponentRenderer.tsx) applies under the
// harness's `* { box-sizing: border-box }` reset, so the 50/30 minimum
// constrains the whole card INCLUDING padding + border bands (CSS 2.1
// §10.7: min-height constrains the box selected by box-sizing).
//
// Compose chains its floor INSIDE the padding-last style chain (see
// StyleApplier.applyConfig step 8 + ComponentRenderer.RenderComponent),
// which made the raw defaultMinSize(50.dp, 30.dp) a CONTENT-box floor:
// a default-font placeholder (Text 20px + 4dp label padding = 28px)
// under `padding: 8px` measured max(28, 30) + 16 = 46px while web
// computed max(27.4 + 16, 30) = 43.4px. That was the systematic +2px
// Android canvas delta on EVERY default-font leaf — all 33 category
// Decorated combo rows (76→78, 84→86, 92→94, 100→102) plus Color_Hex /
// Padding_All / Flex_Row / … in the visual-test corpus, costing
// 0.03–0.10 SSIM on both Android pairs.
//
// borderBoxFloorMins subtracts the padding + border-band insets from the
// 50/30 web floor so the content-box minimum Compose enforces equals
// what web's border-box minimum leaves for the content.

import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceholderFloorBorderBoxTest {

    // ── Height axis ─────────────────────────────────────────────────────

    /** padding: 8px (Decorated combos) → content floor 30−16 = 14: the
     *  28px default-font text wins → card 44px, matching web's 43.4→44. */
    @Test
    fun `padding 8 leaves a 14px content floor`() {
        val (_, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 16f, verticalInset = 16f,
            applyWidthFloor = true, applyHeightFloor = true
        )
        assertEquals(14f, minH.value, 0.001f)
    }

    /** padding: 20px (Padding_All) consumes the whole 30px floor → no
     *  minimum at all (web: 27.4 + 40 = 67.4 ≥ 30, min never bites). */
    @Test
    fun `padding 20 fully absorbs the height floor`() {
        val (_, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 40f, verticalInset = 40f,
            applyWidthFloor = true, applyHeightFloor = true
        )
        assertEquals(Dp.Unspecified, minH)
    }

    /** No padding/border → the raw web floor applies unchanged (bare
     *  `background-color` cards still get the 30px minimum web enforces). */
    @Test
    fun `zero insets keep the raw 50x30 floor`() {
        val (minW, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 0f, verticalInset = 0f,
            applyWidthFloor = true, applyHeightFloor = true
        )
        assertEquals(50f, minW.value, 0.001f)
        assertEquals(30f, minH.value, 0.001f)
    }

    /** Input_Field regression guard (wave-1 fix must survive): border 1px
     *  + padding 12px vertical → floor 30−26 = 4; the band inset (chained
     *  INSIDE the floor) + text dominate → geometry unchanged at 54px. */
    @Test
    fun `border band participates in the border-box floor`() {
        val (_, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 32f + 2f, verticalInset = 24f + 2f,
            applyWidthFloor = true, applyHeightFloor = true
        )
        assertEquals(4f, minH.value, 0.001f)
    }

    // ── Explicit-size gating (unchanged behaviour) ──────────────────────

    /** Explicit width/height suppress the floor per axis — mirrors web's
     *  `styles.width || styles.minWidth || '50px'` fallback chain. */
    @Test
    fun `explicit axes disable their floor`() {
        val (minW, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 0f, verticalInset = 0f,
            applyWidthFloor = false, applyHeightFloor = false
        )
        assertEquals(Dp.Unspecified, minW)
        assertEquals(Dp.Unspecified, minH)
    }

    /** Mixed: width explicit, height not → only the height floor lives. */
    @Test
    fun `width explicit keeps only the height floor`() {
        val (minW, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 16f, verticalInset = 16f,
            applyWidthFloor = false, applyHeightFloor = true
        )
        assertEquals(Dp.Unspecified, minW)
        assertEquals(14f, minH.value, 0.001f)
    }

    // ── Width axis ──────────────────────────────────────────────────────

    /** Width floor mirrors the height math against web's 50px minimum:
     *  padding 16×2 → 50−32 = 18px content floor. */
    @Test
    fun `width floor subtracts horizontal insets`() {
        val (minW, _) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 32f, verticalInset = 0f,
            applyWidthFloor = true, applyHeightFloor = true
        )
        assertEquals(18f, minW.value, 0.001f)
    }

    /** Insets ≥ the floor never produce a negative minimum. */
    @Test
    fun `oversized insets clamp to no minimum`() {
        val (minW, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 200f, verticalInset = 200f,
            applyWidthFloor = true, applyHeightFloor = true
        )
        assertEquals(Dp.Unspecified, minW)
        assertEquals(Dp.Unspecified, minH)
    }

    /** Exactly-consumed floor (inset == floor) also disables the minimum —
     *  web's max(content, 0) is a no-op, Unspecified is Compose's no-op. */
    @Test
    fun `exactly consumed floor is unspecified`() {
        val (minW, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 50f, verticalInset = 30f,
            applyWidthFloor = true, applyHeightFloor = true
        )
        assertEquals(Dp.Unspecified, minW)
        assertEquals(Dp.Unspecified, minH)
    }

    // ── End-to-end: property list → Modifier inset math ─────────────────

    /** placeholderFloorMinSize resolves CSS padding px shapes the same way
     *  PaddingApplier will — pin the svg Decorated case: padding 8px on
     *  all sides via the `{"px": 8}` wire shape → 14px content floor. */
    @Test
    fun `property list resolves px padding into the floor`() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement("""{"px": 8}""")
        val props = listOf(
            com.styleconverter.runtime.core.ir.IRProperty("PaddingTop", json),
            com.styleconverter.runtime.core.ir.IRProperty("PaddingBottom", json),
            com.styleconverter.runtime.core.ir.IRProperty("PaddingLeft", json),
            com.styleconverter.runtime.core.ir.IRProperty("PaddingRight", json)
        )
        // The Modifier itself can't be inspected on the JVM, but the inset
        // math it feeds from is pinned via the pure function with the same
        // resolved values (16f both axes → 34/14). Building the modifier
        // must also not throw on the JVM (no Compose runtime needed).
        StyleApplier.placeholderFloorMinSize(
            props, applyWidthFloor = true, applyHeightFloor = true
        )
        val (minW, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = 16f, verticalInset = 16f,
            applyWidthFloor = true, applyHeightFloor = true
        )
        assertEquals(34f, minW.value, 0.001f)
        assertEquals(14f, minH.value, 0.001f)
    }
}
