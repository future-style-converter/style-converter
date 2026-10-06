package com.styleconverter.runtime.core.renderer

// Retrospective R3 (finding A5#4) — the placeholder's overflow DECISION for a
// clamp whose marker the author forbade (css-overflow-4 §5.1 two-value
// `line-clamp`; §4.2 `block-ellipsis: no-ellipsis` / the empty string).
//
// Compose's Text has ONE overflow knob: TextOverflow.Ellipsis on a clamped
// run paints "…" at the end of line N — the marker -023/-024 forbid — while
// Clip discards without a glyph. Swift cannot do the latter through
// `.lineLimit`, so it re-routes the marker-less clamp through its height cap
// (LineClampCap.leafLineLimit → nil); Compose only needs the decision.
//
// Wires are VERBATIM from tools/titan/runs/wave49-final/sections/css-overflow/
// per-test-ir/wpt__css-overflow__line-clamp__block-ellipsis-023.json / -024.
// Both already Clip today (no `text-overflow` declared → the renderer's
// default), which is why they pass (Android-web 0.9735); the pin that needed
// the seam is the explicit-`text-overflow: ellipsis` case, where the old
// "author decision always wins" line returned Ellipsis and would have
// painted the forbidden marker. Mutation proof: without the
// placeholderOverflow branch this class's `even over an explicit
// text-overflow ellipsis` test fails (Ellipsis returned).
//
// Wave 52 (lane L9, F3) — the OTHER half of the same decision: a bare
// `line-clamp: N` on a SOFT-WRAPPED run must paint the UA marker
// (css-overflow-4 §5.1 `line-clamp: <n>` ⇒ `block-ellipsis: auto`; §4.2
// `auto` = the UA ellipsis). Until wave 52 the clamp branch returned
// `declared`, which is Compose's Clip default whenever no `text-overflow`
// is on the wire — so NO Compose clamp in the corpus ever painted "…"
// (block-ellipsis-001 android P 0.9836 = `…room uncha` clipped; -032
// android f 0.9397: fold landed, no marker in any box) while the
// config-driven LineClampApplier.getTextOverflow said Ellipsis all along.
// Wires below are VERBATIM from wave51-fix/sections/css-overflow/per-test-ir.
// MUTATION PROOF (executed 2026-10-05, lane L9, by
// tools/titan/results/wave52-inline-run-wall/mutate.py on an isolated
// HEAD + lane + seam-1.patch copy; record in mutations-compose.result.json):
// M6 — the Ellipsis arm reverted to `return declared`: `a bare line-clamp
// on a soft-wrapped run answers Ellipsis`, `a drawn marker keeps the pre-R3
// decision` and LineClampUnderPreWave39Test's rewritten pin fail (Clip
// returned); M7 — DrawnLineClamp.cap consulting ALL properties instead of
// the LineClamp declarations: `max-lines alone paints no marker` and
// `DrawnLineClamp names the fixed cap …` fail. Restored byte-exact,
// sha256-verified. The other half of the seam — threading
// `unclippedLineWidths = !ruleBSoftWrap` instead of `!wrapConfig.softWrap`
// — is a call-site argument inside the composable and has NO unit pin
// (stated in the lane note); `the same run pre-broken answers Visible`
// pins only the function's answer once that flag arrives.

import androidx.compose.ui.text.style.TextOverflow
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.typography.wrapping.DrawnLineClamp
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceholderOverflowMarkerTest {

    private fun prop(t: String, s: String) = IRProperty(t, Json.parseToJsonElement(s))

    /** block-ellipsis-023's clamp, verbatim: `line-clamp: 4 no-ellipsis`. */
    private val clamp023 = prop("LineClamp", """{"type":"lines","count":4,"ellipsis":{"type":"no-ellipsis"}}""")

    /** block-ellipsis-024's clamp, verbatim: `line-clamp: 4 ""`. */
    private val clamp024 = prop("LineClamp", """{"type":"lines","count":4,"ellipsis":{"type":"string","value":""}}""")

    /** A plain `line-clamp: 4` — the marker longhand at its initial value. */
    private val plain4 = prop("LineClamp", """{"type":"lines","count":4}""")

    /** The rest of -023's component list, verbatim (32ch monospace box). */
    private val box = listOf(
        prop("Width", """{"type":"length","original":{"v":32,"u":"CH"}}"""),
        prop("FontFamily", """["monospace"]"""),
    )

    /** An explicit `text-overflow: ellipsis` declaration (any wire shape —
     *  the decision only tests for the property's presence). */
    private val textOverflowEllipsis = prop("TextOverflow", "\"ellipsis\"")

    @Test
    fun `the marker-less clamp Clips on the leaf path`() {
        // Today's outcome for -023 / -024, now pinned as the decision.
        for (clamp in listOf(clamp023, clamp024)) {
            val properties = listOf(clamp) + box
            assertEquals(4, ComponentRenderer.placeholderMaxLines(properties))
            assertEquals(
                TextOverflow.Clip,
                ComponentRenderer.placeholderOverflow(properties, effectiveMaxLines = 4, declared = TextOverflow.Clip)
            )
        }
    }

    @Test
    fun `even over an explicit text-overflow ellipsis`() {
        // The seam: `text-overflow` governs INLINE-axis overflow (css-overflow-3
        // §6.1) and has nothing to paint on a wrapped, block-clamped run; the
        // forbidden block marker must not be painted through it.
        val properties = listOf(clamp023, textOverflowEllipsis) + box
        assertEquals(
            TextOverflow.Clip,
            ComponentRenderer.placeholderOverflow(properties, effectiveMaxLines = 4, declared = TextOverflow.Ellipsis)
        )
    }

    @Test
    fun `a drawn marker keeps the pre-R3 decision byte-identically`() {
        // Plain `line-clamp: 4` + explicit ellipsis → Ellipsis (author wins).
        assertEquals(
            TextOverflow.Ellipsis,
            ComponentRenderer.placeholderOverflow(listOf(plain4, textOverflowEllipsis) + box, effectiveMaxLines = 4, declared = TextOverflow.Ellipsis)
        )
        // Plain `line-clamp: 4`, nothing declared → wave 52 (F3): the UA
        // marker (css-overflow-4 §5.1/§4.2), no longer the Clip default that
        // pinned the missing "…" on every Compose clamp.
        assertEquals(
            TextOverflow.Ellipsis,
            ComponentRenderer.placeholderOverflow(listOf(plain4) + box, effectiveMaxLines = 4, declared = TextOverflow.Clip)
        )
        // No clamp at all → CSS's initial `overflow: visible` (unchanged).
        assertEquals(
            TextOverflow.Visible,
            ComponentRenderer.placeholderOverflow(box, effectiveMaxLines = Int.MAX_VALUE, declared = TextOverflow.Clip)
        )
    }

    @Test
    fun `pre and nowrap runs keep their Visible`() {
        // Wave 39's `unclippedLineWidths` arm: a preserved-whitespace clamped
        // run paints past the box with no marker either way — the marker
        // rule must not turn that Visible into a Clip.
        assertEquals(
            TextOverflow.Visible,
            ComponentRenderer.placeholderOverflow(
                listOf(clamp023) + box, effectiveMaxLines = 4, declared = TextOverflow.Clip, unclippedLineWidths = true
            )
        )
    }

    // ── Wave 52 (lane L9, F3) — the drawn marker on a soft-wrapped clamp ──

    /** block-ellipsis-032's first clamp box, verbatim (minus the four
     *  border longhand triplets, which the decision never reads): the
     *  `line-clamp: 1` host whose fold landed on Android in wave 50 but
     *  painted no "…" (android f 0.9397; the ref: `This text is
     *  left-aligned…`). */
    private val box032 = listOf(
        prop("LineClamp", """{"type":"lines","count":1}"""),
        prop("FontFamily", """["monospace"]"""),
        prop("MarginBottom", """{"original":{"v":1,"u":"EM"}}"""),
        prop("Width", """{"type":"length","original":{"v":29,"u":"CH"}}"""),
    )

    @Test
    fun `a bare line-clamp on a soft-wrapped run answers Ellipsis`() {
        assertEquals(1, ComponentRenderer.placeholderMaxLines(box032))
        assertEquals(
            TextOverflow.Ellipsis,
            ComponentRenderer.placeholderOverflow(box032, effectiveMaxLines = 1, declared = TextOverflow.Clip)
        )
        // -001's plain `line-clamp: 4` on a 32ch box, the passing-wrong shape.
        assertEquals(
            TextOverflow.Ellipsis,
            ComponentRenderer.placeholderOverflow(listOf(plain4) + box, effectiveMaxLines = 4, declared = TextOverflow.Clip)
        )
    }

    @Test
    fun `the same run pre-broken answers Visible`() {
        // The seam threads `unclippedLineWidths = !ruleBSoftWrap`: a rule-B
        // pre-broken run (block-ellipsis-025's overlong word) or a B-RC7
        // unbreakable run renders with softWrap = false, where Ellipsis is
        // the wave-39 finalMaxLines landmine — and its lines lay out at
        // intrinsic width like `pre`, so Visible is the CSS answer. The
        // marker for that run is BAKED by PreBreakPipeline instead (F4).
        assertEquals(
            TextOverflow.Visible,
            ComponentRenderer.placeholderOverflow(
                box032, effectiveMaxLines = 1, declared = TextOverflow.Clip, unclippedLineWidths = true
            )
        )
    }

    @Test
    fun `max-lines alone paints no marker`() {
        // css-overflow-4 §5.1: only the `line-clamp` shorthand sets
        // block-ellipsis to `auto`; the `max-lines` longhand leaves it at
        // its initial `none`, so the clamp discards without a glyph (the
        // declared default, Clip). Wire shape transcribed from the
        // converter (`max-lines: 3` → {"type":"count","value":3.0}).
        val maxLines = listOf(prop("MaxLines", """{"type":"count","value":3.0}""")) + box
        assertEquals(3, ComponentRenderer.placeholderMaxLines(maxLines))
        assertEquals(
            TextOverflow.Clip,
            ComponentRenderer.placeholderOverflow(maxLines, effectiveMaxLines = 3, declared = TextOverflow.Clip)
        )
        // `line-clamp: none` beside `max-lines` resets block-ellipsis too.
        val reset = listOf(prop("LineClamp", """{"type":"none"}""")) + maxLines
        assertEquals(
            TextOverflow.Clip,
            ComponentRenderer.placeholderOverflow(reset, effectiveMaxLines = 3, declared = TextOverflow.Clip)
        )
    }

    @Test
    fun `DrawnLineClamp names the fixed cap only when a marker is drawn`() {
        // The one reader both the F3 decision and the F4 pipeline consult.
        assertEquals(1, DrawnLineClamp.cap(box032))
        assertEquals(4, DrawnLineClamp.cap(listOf(plain4) + box))
        // -023 / -024: the author forbade the marker.
        assertEquals(null, DrawnLineClamp.cap(listOf(clamp023) + box))
        assertEquals(null, DrawnLineClamp.cap(listOf(clamp024) + box))
        // `line-clamp: none`, the bare longhand, no clamp at all.
        assertEquals(null, DrawnLineClamp.cap(listOf(prop("LineClamp", """{"type":"none"}""")) + box))
        assertEquals(null, DrawnLineClamp.cap(listOf(prop("MaxLines", """{"type":"count","value":3.0}""")) + box))
        assertEquals(null, DrawnLineClamp.cap(box))
    }
}
