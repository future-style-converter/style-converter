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
// borderBoxFloorMins subtracts placeholderFloorInsets (the CSS PADDING;
// wave 50 removed the border-band term — see BACKLOG queue 9(f) and the
// note on placeholderFloorInsets) from the 50/30 web floor, so the
// content-box minimum Compose enforces equals what web's border-box
// minimum leaves for the content.

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

    /** Wave 50 (BACKLOG queue 9(f)) — the band is NOT subtracted from the
     *  floor. It is chained INSIDE the floor node (borderContentInset comes
     *  after placeholderFloor in ComponentRenderer's `.then` chain), so it
     *  is part of the floor's own child measurement; subtracting it too
     *  removed it from the total and every floor-bound bordered placeholder
     *  came out 2x(border-width) short. Input_Field: border 1px, padding
     *  12px/16px → the floor gives back PADDING only, 30−24 = 6. */
    @Test
    fun `the border band is not subtracted from the floor`() {
        val props = bordered(paddingPx = 12f, horizontalPaddingPx = 16f, borderPx = 1f)
        val (h, v) = StyleApplier.placeholderFloorInsets(props)
        assertEquals(32f, h, 0.001f)
        assertEquals(24f, v, 0.001f)
        val (_, minH) = StyleApplier.borderBoxFloorMins(
            horizontalInset = h, verticalInset = v,
            applyWidthFloor = true, applyHeightFloor = true
        )
        assertEquals(6f, minH.value, 0.001f)
    }

    /** The three committed baselines the ledger owner
     *  `android-harness-placeholder-floor` names, reproduced END TO END
     *  through the same arithmetic the Compose chain performs:
     *
     *      total = padding + max(content + band, minH)
     *
     *  with `content = 0` (visual-test.json has no text — the floor exists
     *  only because of that) and web's answer `max(content + band + padding,
     *  30)`. MEASURED ink bounding boxes of the committed baselines before
     *  this change: Android 115x26 / 200x28 / 115x26 against web 115x30 /
     *  200x30 / 115x30 (tools/visual/baseline/{Android,web}__091_Button_
     *  Outline.png, __094_Input_Field.png, __105_Edge_DeepNesting.png). */
    @Test
    fun `the three ledgered components now match web's border-box height`() {
        // name, vertical padding total, horizontal padding total, border px
        val cases = listOf(
            Triple("091_Button_Outline", 20f, 4f),   // padding 10px 22px, border 2px
            Triple("094_Input_Field", 24f, 2f),      // padding 12px 16px, border 1px
            Triple("105_Edge_DeepNesting", 20f, 4f)  // padding 10px,      border 2px
        )
        for ((name, paddingV, band) in cases) {
            val (_, minH) = StyleApplier.borderBoxFloorMins(
                horizontalInset = 0f, verticalInset = paddingV,
                applyWidthFloor = false, applyHeightFloor = true
            )
            // Compose: padding + max(content + band, minH), content = 0.
            val android = paddingV + maxOf(0f + band, minH.value)
            // Web: max(content + band + padding, 30) under border-box.
            val web = maxOf(0f + band + paddingV, 30f)
            assertEquals(name, web, android, 0.001f)
            assertEquals(name, 30f, android, 0.001f)
        }
    }

    /** The same three components under the OLD math (band subtracted too),
     *  pinned so the regression this fix removes stays legible and so the
     *  arithmetic that produced the measured 26/28/26 is on the record —
     *  the STATUS caveat is that this arithmetic regressed twice before
     *  (waves 1 and 4), and a number nobody can re-derive is how. */
    @Test
    fun `the old band-subtracting math reproduces the measured short heights`() {
        val cases = listOf(
            Triple("091_Button_Outline", 20f, 4f) to 26f,
            Triple("094_Input_Field", 24f, 2f) to 28f,
            Triple("105_Edge_DeepNesting", 20f, 4f) to 26f
        )
        for ((c, expected) in cases) {
            val (name, paddingV, band) = c
            val (_, oldMinH) = StyleApplier.borderBoxFloorMins(
                horizontalInset = 0f, verticalInset = paddingV + band,
                applyWidthFloor = false, applyHeightFloor = true
            )
            val oldAndroid = paddingV + maxOf(0f + band, oldMinH.value)
            assertEquals(name, expected, oldAndroid, 0.001f)
        }
    }

    /** Bordered components whose padding ALONE already exceeds the floor are
     *  untouched by the correction — Border_Solid (15px padding, 3px border),
     *  Border_Dashed (15px, 2px) and Glass_Effect (20px, 1px) are exact on
     *  all three platforms today (baseline ink 97x36 / 102x34 / 100x42
     *  identical on web, iOS and Android) and must stay exact. */
    @Test
    fun `bordered components above the floor are unaffected`() {
        for ((name, paddingV) in listOf(
            "016_Border_Solid" to 30f, "017_Border_Dashed" to 30f, "097_Glass_Effect" to 40f
        )) {
            val (_, minH) = StyleApplier.borderBoxFloorMins(
                horizontalInset = 0f, verticalInset = paddingV,
                applyWidthFloor = false, applyHeightFloor = true
            )
            // Padding ≥ 30 consumes the whole floor: no minimum at all, so
            // neither the old nor the new inset can change the geometry.
            assertEquals(name, Dp.Unspecified, minH)
        }
    }

    /** Helper: the property-list shape a bordered, padded component has on
     *  the wire — `{"px": n}` padding longhands plus a solid border side. */
    private fun bordered(
        paddingPx: Float,
        horizontalPaddingPx: Float,
        borderPx: Float
    ): List<com.styleconverter.runtime.core.ir.IRProperty> {
        fun px(v: Float) = kotlinx.serialization.json.Json.parseToJsonElement("""{"px": $v}""")
        fun w(v: Float) = kotlinx.serialization.json.Json.parseToJsonElement("""{"px": $v}""")
        return listOf(
            com.styleconverter.runtime.core.ir.IRProperty("PaddingTop", px(paddingPx)),
            com.styleconverter.runtime.core.ir.IRProperty("PaddingBottom", px(paddingPx)),
            com.styleconverter.runtime.core.ir.IRProperty("PaddingLeft", px(horizontalPaddingPx)),
            com.styleconverter.runtime.core.ir.IRProperty("PaddingRight", px(horizontalPaddingPx)),
            com.styleconverter.runtime.core.ir.IRProperty("BorderTopWidth", w(borderPx)),
            com.styleconverter.runtime.core.ir.IRProperty("BorderBottomWidth", w(borderPx)),
            com.styleconverter.runtime.core.ir.IRProperty("BorderLeftWidth", w(borderPx)),
            com.styleconverter.runtime.core.ir.IRProperty("BorderRightWidth", w(borderPx)),
            com.styleconverter.runtime.core.ir.IRProperty(
                "BorderTopStyle", kotlinx.serialization.json.Json.parseToJsonElement("\"SOLID\"")),
            com.styleconverter.runtime.core.ir.IRProperty(
                "BorderBottomStyle", kotlinx.serialization.json.Json.parseToJsonElement("\"SOLID\"")),
            com.styleconverter.runtime.core.ir.IRProperty(
                "BorderLeftStyle", kotlinx.serialization.json.Json.parseToJsonElement("\"SOLID\"")),
            com.styleconverter.runtime.core.ir.IRProperty(
                "BorderRightStyle", kotlinx.serialization.json.Json.parseToJsonElement("\"SOLID\""))
        )
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
