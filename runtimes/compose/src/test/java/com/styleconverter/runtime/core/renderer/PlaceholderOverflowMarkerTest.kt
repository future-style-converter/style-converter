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

import androidx.compose.ui.text.style.TextOverflow
import com.styleconverter.runtime.core.ir.IRProperty
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
        // Plain `line-clamp: 4`, nothing declared → the declared default (Clip).
        assertEquals(
            TextOverflow.Clip,
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
}
