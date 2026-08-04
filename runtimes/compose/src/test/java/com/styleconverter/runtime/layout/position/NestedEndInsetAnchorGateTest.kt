package com.styleconverter.runtime.layout.position

// Wave 28 (lane NE, skeptic repair) — pins for
// [PositionedAncestorAnchor.anchorGate]: the raw-wire/resolved-wire
// disagreement that the bare extractor let through, plus the wave-27
// skeptic's own end-to-end probe arithmetic.
//
// The hazard in one line: this mount reads `child.properties` (the RAW
// wire) while the child's style chain reads the same list AFTER
// DynamicValueResolver has substituted var(), evaluated calc() and
// rewritten em/rem to px. Where the two disagree about whether a START
// inset is declared, the anchor and the negated offset measure from
// OPPOSITE edges. Every case below is executed against the live resolver
// so the pin cannot drift from the real chain.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import com.styleconverter.runtime.core.variables.DynamicValueResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedEndInsetAnchorGateTest {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun p(t: String, d: String) = IRProperty(type = t, data = j(d))

    /** The anchor decision the mount makes, vs the one the style chain implies. */
    private fun agree(raw: List<IRProperty>): Pair<Boolean, Boolean> {
        val mount = PositionedAncestorAnchor.anchorGate(raw.map { it.type to it.data })
        val chain = PositionExtractor.extractPositionConfig(
            DynamicValueResolver.resolve(raw, emptyMap(), DynamicValueResolver.Context())
                .properties.map { it.type to it.data }
        )
        return mount.anchorsFromEndX to chain.anchorsFromEndX
    }

    /**
     * G1 — the defect. `left: calc(10px + 5px); right: 20px`: the raw wire
     * is `{"expr":"calc(10px + 5px)"}` (extractDp → null), the resolved
     * wire is 15dp. Ungated, the mount anchored from the END while
     * PositionApplier emitted `+15.dp`, composing to `cb − box + 15`
     * (255 on a 300px containing block with a 60px box) where the correct
     * used position is 15 — WORSE than the pre-wave-28 start anchor.
     */
    @Test
    fun `calc start inset withdraws the end anchor`() {
        val raw = listOf(
            p("Position", "\"ABSOLUTE\""),
            p("Left", "{\"expr\":\"calc(10px + 5px)\"}"),
            p("Right", "{\"px\":20.0}"),
        )
        val (mount, chain) = agree(raw)
        assertFalse("mount must not anchor from the end", mount)
        assertEquals("mount and style chain must agree", chain, mount)
        // And the whole box is off the anchored mount entirely.
        assertFalse(
            PositionedAncestorAnchor.anchors(
                PositionedAncestorAnchor.anchorGate(raw.map { it.type to it.data })
            )
        )
    }

    /** G1b — the var() spelling of the same wire, and the block axis. */
    @Test
    fun `var start inset withdraws the end anchor on either axis`() {
        val raw = listOf(
            p("Position", "\"ABSOLUTE\""),
            p("Top", "{\"expr\":\"var(--t)\"}"),
            p("Bottom", "{\"px\":20.0}"),
        )
        val gated = PositionedAncestorAnchor.anchorGate(raw.map { it.type to it.data })
        assertFalse(gated.anchorsFromEndY)
        assertFalse(PositionedAncestorAnchor.anchors(gated))
    }

    /**
     * G2 — a PERCENT start inset is likewise unreadable here (the resolver
     * only rewrites % for Padding/Margin/FontSize carriers, so both halves
     * currently read null — but the gate must not depend on that, since a
     * future resolver revision would flip the chain and not the mount).
     */
    @Test
    fun `percent start inset withdraws the end anchor`() {
        val gated = PositionedAncestorAnchor.anchorGate(
            listOf(
                "Position" to j("\"ABSOLUTE\""),
                "Left" to j("{\"percentage\":25.0}"),
                "Right" to j("{\"px\":20.0}"),
            )
        )
        assertFalse(gated.anchorsFromEndX)
    }

    /**
     * G3 — `auto` is the INITIAL value, not a declaration: an
     * `left: auto; right: 20px` box still anchors from the end
     * (css-position-3 §3.5.3). The converter emits the bare string
     * `"auto"`, which extractDp cannot read either — so without the
     * keyword carve-out the gate would kill the fixture the lane exists
     * for (position-right-bottom's sibling spelling).
     */
    @Test
    fun `auto start inset keeps the end anchor`() {
        val gated = PositionedAncestorAnchor.anchorGate(
            listOf(
                "Position" to j("\"ABSOLUTE\""),
                "Left" to j("\"auto\""),
                "Top" to j("\"auto\""),
                "Right" to j("{\"px\":20.0}"),
                "Bottom" to j("{\"px\":20.0}"),
            )
        )
        assertTrue(gated.anchorsFromEndX)
        assertTrue(gated.anchorsFromEndY)
    }

    /** G4 — the em wire IS readable raw; nothing is withdrawn, both agree. */
    @Test
    fun `em start inset is readable on both sides`() {
        val raw = listOf(
            p("Position", "\"ABSOLUTE\""),
            p("Left", "{\"original\":{\"v\":1.0,\"u\":\"EM\"}}"),
            p("Right", "{\"px\":20.0}"),
        )
        val (mount, chain) = agree(raw)
        assertFalse(mount)
        assertEquals(chain, mount)
    }

    /** G5 — the plain end-only box is untouched by the gate. */
    @Test
    fun `gate is identity for the plain end-anchored box`() {
        val props = listOf(
            "Position" to j("\"ABSOLUTE\""),
            "Right" to j("{\"px\":30.0}"),
            "Bottom" to j("{\"px\":30.0}"),
        )
        val gated = PositionedAncestorAnchor.anchorGate(props)
        assertEquals(PositionExtractor.extractPositionConfig(props), gated)
        assertTrue(PositionedAncestorAnchor.anchors(gated))
    }

    /**
     * G6 — the wave-27 skeptic's probe, end to end in whole px at
     * density 1: a 30×30 `right:30px; bottom:30px` child of a 340×340
     * positioned parent lands at parent-relative (280, 280).
     */
    @Test
    fun `wave27 probe lands at 280 280`() {
        val cfg = PositionedAncestorAnchor.anchorGate(
            listOf(
                "Position" to j("\"ABSOLUTE\""),
                "Right" to j("{\"px\":30.0}"),
                "Bottom" to j("{\"px\":30.0}"),
            )
        )
        val fillX = PositionedAncestorAnchor.axisFillPx(cfg.anchorsFromEndX, 340)
        val fillY = PositionedAncestorAnchor.axisFillPx(cfg.anchorsFromEndY, 340)
        // The slot reports the containing block — no slack for the parent
        // Box's direction-aware TopStart alignment to hand out.
        assertEquals(340, PositionedAncestorAnchor.axisReportPx(fillX, 30, 0, 340))
        assertEquals(340, PositionedAncestorAnchor.axisReportPx(fillY, 30, 0, 340))
        // Anchor half (this slot) + offset half (PositionApplier).
        val anchorX = PositionedAncestorAnchor.axisPlacePx(fillX, 30)
        val anchorY = PositionedAncestorAnchor.axisPlacePx(fillY, 30)
        assertEquals(310, anchorX)
        assertEquals(310, anchorY)
        assertEquals(-30f, cfg.offsetX.value, 0f)
        assertEquals(-30f, cfg.offsetY.value, 0f)
        assertEquals(280, anchorX + cfg.offsetX.value.toInt())
        assertEquals(280, anchorY + cfg.offsetY.value.toInt())
    }

    /**
     * G8 — css-logical-1 §4.1: `inset-inline-end` on an RTL containing
     * block means the physical LEFT edge, but PositionConfig folds it into
     * the physical `end` slot regardless of direction. The slot withdraws
     * rather than anchoring the box a whole containing block away from the
     * browser's position — the live witness is audit-phase12's
     * Inset_Logical_RTL (see [PositionedAncestorAnchor.inlineAxisAnchorsFromEnd]).
     */
    @Test
    fun `logical inline-end withdraws under rtl but keeps ltr`() {
        val cfg = PositionExtractor.extractPositionConfig(
            listOf(
                "Position" to j("\"ABSOLUTE\""),
                "InsetInlineEnd" to j("{\"px\":10.0}"),
                "InsetBlockEnd" to j("{\"px\":10.0}"),
            )
        )
        assertTrue(cfg.anchorsFromEndX)
        assertTrue(PositionedAncestorAnchor.inlineAxisAnchorsFromEnd(cfg, isRtl = false))
        assertFalse(PositionedAncestorAnchor.inlineAxisAnchorsFromEnd(cfg, isRtl = true))
        // The BLOCK axis is unaffected: horizontal-tb block flow does not
        // depend on `direction` (only on writing-mode, which is not folded).
        assertTrue(cfg.anchorsFromEndY)
        // Withdrawn ⇒ the wave-8 report + slot-origin placement.
        val fillX = PositionedAncestorAnchor.axisFillPx(
            PositionedAncestorAnchor.inlineAxisAnchorsFromEnd(cfg, isRtl = true), 300)
        assertEquals(80, PositionedAncestorAnchor.axisReportPx(fillX, 80, 0, 300))
        assertEquals(0, PositionedAncestorAnchor.axisPlacePx(fillX, 80))
    }

    /** G9 — a PHYSICAL `right` is direction-independent (css-position-3 §3.1). */
    @Test
    fun `physical right keeps the end anchor under rtl`() {
        val cfg = PositionExtractor.extractPositionConfig(
            listOf("Position" to j("\"ABSOLUTE\""), "Right" to j("{\"px\":20.0}"))
        )
        assertTrue(PositionedAncestorAnchor.inlineAxisAnchorsFromEnd(cfg, isRtl = true))
        assertTrue(PositionedAncestorAnchor.inlineAxisAnchorsFromEnd(cfg, isRtl = false))
    }

    /**
     * G7 — the byte-stability pin restated against the gate: an axis the
     * gate withdrew reports EXACTLY what wave 8 reported, so a withdrawn
     * box is indistinguishable from the pre-wave-28 render.
     */
    @Test
    fun `withdrawn axis reports the wave8 size`() {
        for (ink in listOf(0, 10, 49, 50, 51, 500)) {
            assertEquals(
                ComponentRenderer.absposReportedAxis(ink, 0, 50),
                PositionedAncestorAnchor.axisReportPx(null, ink, 0, 50)
            )
            assertEquals(0, PositionedAncestorAnchor.axisPlacePx(null, ink))
        }
    }
}
