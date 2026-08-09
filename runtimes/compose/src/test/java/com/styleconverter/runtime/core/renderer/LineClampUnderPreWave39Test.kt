package com.styleconverter.runtime.core.renderer

// LineClampUnderPreWave39Test.kt
// Wave 39 (lane A3) — css-overflow-4 §5 `line-clamp` must survive
// css-text-3 §4.1.1 `white-space: pre|nowrap`.
//
// THE DEFECT, measured on the wave38-final composed WPT gate (android-ref
// SSIM, iOS on the same test in brackets):
//
//   css-overflow/line-clamp/line-clamp-004        0.9303 FAIL  [0.9902 pass]
//   css-overflow/line-clamp/line-clamp-005        0.9476 FAIL  [0.9538 pass]
//   css-overflow/line-clamp/line-clamp-006        0.9426 FAIL  [0.9525 pass]
//   css-overflow/line-clamp/line-clamp-007        0.9426 FAIL  [0.9525 pass]
//   css-overflow/line-clamp/block-ellipsis-013    0.9089 FAIL  [0.9652 pass]
//   css-overflow/line-clamp/block-ellipsis-014    0.9089 FAIL  [0.9652 pass]
//   css-overflow/line-clamp/block-ellipsis-017    0.9089 FAIL  [0.9652 pass]
//   css-overflow/line-clamp/block-ellipsis-026    0.8843 FAIL  [0.9704 pass]
//   css-overflow/line-clamp/line-clamp-001        0.9542 pass  [0.9905 pass]
//
// EVERY one of those nine declares `white-space: pre` alongside its
// `line-clamp`, and ComponentRenderer used to answer Int.MAX_VALUE (=
// uncapped) for any run whose softWrap was off. line-clamp-004's capture
// showed all five `Line N` rows where Chromium, the web runtime and iOS all
// show four — and the surplus row shoved the following paragraph down the
// page, which is where most of the SSIM went.
//
// These pin the DECISION (pure), not the pixels: the cap is read from the
// IR alone and no white-space keyword may change it. The property lists
// below are transcribed from the per-test IR the titan run fed the harness.

import androidx.compose.ui.text.style.TextOverflow
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LineClampUnderPreWave39Test {

    private fun prop(t: String, s: String) = IRProperty(t, Json.parseToJsonElement(s))

    /** `line-clamp: 4` exactly as the converter wires it. */
    private fun lineClamp(count: Int) =
        prop("LineClamp", """{"type":"lines","count":$count}""")

    private fun whiteSpace(mode: String) = prop("WhiteSpace", "\"$mode\"")

    // ── the regression itself ────────────────────────────────────────────

    @Test
    fun `white-space pre does not delete the line-clamp cap`() {
        // wpt/css-overflow/line-clamp/line-clamp-004: `line-clamp: 4` +
        // `white-space: pre` over a five-line preserved-newline run.
        val properties = listOf(
            lineClamp(4),
            prop("FontSize", """{"px":16.0}"""),
            whiteSpace("PRE"),
            prop("BackgroundColor", """{"srgb":{"r":1.0,"g":1.0,"b":0.0}}""")
        )
        assertEquals(4, ComponentRenderer.placeholderMaxLines(properties))
    }

    @Test
    fun `white-space nowrap does not delete the line-clamp cap`() {
        // The other softWrap = false keyword. CSS gives a nowrap run one
        // line box anyway, so the cap is inert there — but it must be the
        // LAYOUT that produces one line, not a silently dropped property.
        val properties = listOf(lineClamp(2), whiteSpace("NOWRAP"))
        assertEquals(2, ComponentRenderer.placeholderMaxLines(properties))
    }

    @Test
    fun `the cap is identical with and without the white-space keyword`() {
        // The invariant behind the fix: `white-space` and `line-clamp`
        // answer two different questions (where soft wrap opportunities
        // are, versus how many line boxes survive) and never interact.
        val clampOnly = listOf(lineClamp(2))
        for (mode in listOf("PRE", "NOWRAP", "PRE_WRAP", "PRE_LINE", "NORMAL")) {
            assertEquals(
                "white-space: $mode must not change the clamp",
                ComponentRenderer.placeholderMaxLines(clampOnly),
                ComponentRenderer.placeholderMaxLines(clampOnly + whiteSpace(mode))
            )
        }
    }

    @Test
    fun `block-ellipsis-013's property list caps at two lines`() {
        // The monospace/63.1ch family (block-ellipsis-013 / -014 / -017 /
        // -026): `line-clamp: 2` + `white-space: pre` over a four-line run.
        val properties = listOf(
            lineClamp(2),
            prop("Width", """{"type":"length","original":{"v":63.1,"u":"CH"}}"""),
            prop("BorderTopWidth", """{"px":1.0}"""),
            prop("FontFamily", """["monospace"]"""),
            whiteSpace("PRE")
        )
        assertEquals(2, ComponentRenderer.placeholderMaxLines(properties))
    }

    // ── the bound: nothing else may gain a cap ───────────────────────────

    @Test
    fun `a run with no clamp property stays uncapped`() {
        // This is the whole blast-radius argument: `placeholderMaxLines`
        // can only ever differ from the frozen Int.MAX_VALUE when the
        // component carries LineClamp or MaxLines, and no fixture under
        // fixtures/ declares either — so the 327-pair dark stage is
        // untouchable by this change.
        assertEquals(
            Int.MAX_VALUE,
            ComponentRenderer.placeholderMaxLines(
                listOf(whiteSpace("PRE"), prop("FontSize", """{"px":16.0}"""))
            )
        )
        assertEquals(Int.MAX_VALUE, ComponentRenderer.placeholderMaxLines(emptyList()))
    }

    @Test
    fun `line-clamp none stays uncapped under pre`() {
        // `line-clamp: none` is the reset keyword — it must read as "no
        // cap", not as a cap of zero (which Compose's TextDelegate rejects
        // outright; the wave-19 sub-1 guard owns that path).
        assertEquals(
            Int.MAX_VALUE,
            ComponentRenderer.placeholderMaxLines(
                listOf(prop("LineClamp", """{"type":"none"}"""), whiteSpace("PRE"))
            )
        )
    }

    // ── the second axis: the clamp must not start CLIPPING a pre run ─────

    @Test
    fun `a clamped pre run keeps painting past the box`() {
        // css-overflow-4 §5 expands `line-clamp: <n>` to max-lines +
        // block-ellipsis + continue and sets no `overflow` — the
        // `overflow: hidden` requirement was the legacy `-webkit-line-clamp`
        // idiom's. block-ellipsis-013's preserved first line runs off its
        // 63.1ch box to the canvas edge in Chromium, in the web runtime and
        // on iOS; Compose must keep painting it, which is TextOverflow
        // .Visible — exactly what these components got while the clamp was
        // being dropped, so this wave moves only the line cap for them.
        assertEquals(
            TextOverflow.Visible,
            ComponentRenderer.placeholderOverflow(
                properties = listOf(lineClamp(2), whiteSpace("PRE")),
                effectiveMaxLines = 2,
                declared = TextOverflow.Clip,
                unclippedLineWidths = true
            )
        )
    }

    @Test
    fun `a clamped soft-wrapping run keeps the frozen clipping`() {
        // The default parameter value: every call site that existed before
        // wave 39 answers byte-identically, so the block-ellipsis family
        // that already passes cannot move.
        assertEquals(
            TextOverflow.Clip,
            ComponentRenderer.placeholderOverflow(
                properties = listOf(lineClamp(2)),
                effectiveMaxLines = 2,
                declared = TextOverflow.Clip
            )
        )
    }

    @Test
    fun `an explicit text-overflow still wins on a pre run`() {
        // The author asked for it in either direction — the new pre gate is
        // about a BARE line-clamp, never about an explicit declaration.
        assertEquals(
            TextOverflow.Ellipsis,
            ComponentRenderer.placeholderOverflow(
                properties = listOf(prop("TextOverflow", "\"ELLIPSIS\""), lineClamp(2)),
                effectiveMaxLines = 2,
                declared = TextOverflow.Ellipsis,
                unclippedLineWidths = true
            )
        )
    }

    @Test
    fun `an unclamped pre run is unaffected`() {
        // Visible either way — the CSS 2.1 §11.1.1 default the wave-3 policy
        // already produced for every run with no clipping intent.
        assertEquals(
            TextOverflow.Visible,
            ComponentRenderer.placeholderOverflow(
                properties = listOf(whiteSpace("PRE")),
                effectiveMaxLines = Int.MAX_VALUE,
                declared = TextOverflow.Clip,
                unclippedLineWidths = true
            )
        )
    }

    @Test
    fun `MaxLines is honoured under pre too`() {
        // Same rule for the css-overflow-4 longhand: `max-lines` is the
        // half of the `line-clamp` shorthand that does the capping. Wire
        // shape transcribed from the converter (`max-lines: 3` →
        // {"type":"count","value":3.0}), not from the extractor's KDoc,
        // which still describes an older IRNumber-wrapped form.
        assertEquals(
            3,
            ComponentRenderer.placeholderMaxLines(
                listOf(
                    prop("MaxLines", """{"type":"count","value":3.0}"""),
                    whiteSpace("PRE")
                )
            )
        )
    }
}
