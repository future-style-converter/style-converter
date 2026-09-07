package com.styleconverter.runtime.typography

// Retrospective R3 (finding A7#4) — `font-size-adjust` on Compose, pinned on
// VERBATIM payloads. The eight FontSizeAdjust wires below are the running
// converter's output for fixtures/properties/typography/font-size-adjust.json
// (`./gradlew :converter:run --args="convert --from css --to ir -i
// fixtures/properties/typography/font-size-adjust.json -o <dir>"`, 2026-09-04
// on dev 5aa92ed6 — the sealed serializer emits {"type":"none"},
// {"type":"from-font"}, {"type":"number","value":N} and
// {"type":"metric-value","metric":M,"value":N}), and the 28px + cap-height
// 0.7 case is the SHARED payload the iOS twin asserts
// (FidelityWave2Tests.testFontSizeAdjustCapHeightScalesUsedSize → 26.94).
//
// Expected numbers are css-fonts-4 §2.6 arithmetic — used = computed × number
// / metricRatio — on Inter's OS/2 table (sCapHeight 1490, sxHeight 1118,
// unitsPerEm 2048, read off the bundled inter_regular.ttf with fontTools):
//   28 × 0.7 × 2048 / 1490 = 26.9401   cap-height (shared with iOS)
//   22 × 0.5 × 2048 / 1118 = 20.1503   ex-height, and the bare <number>
//   22 × 0.7 × 2048 / 1490 = 21.1673   cap-height on the fixture's 22px
//   16 × 0.5 × 2048 / 1118 = 14.6547   no declared size → the 16px default
// Mutation proof (run during the lane): swapping the two Inter ratios in
// FontSizeAdjust.kt fails the cap-height and ex-height pins (26.94 → 35.9).

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontSizeAdjustTest {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun prop(type: String, json: String) = IRProperty(type, j(json))

    /** The fixture's `font-size: 22px`, verbatim converter wire. */
    private val fontSize22 = prop("FontSize", """{"px":22.0,"original":{"type":"length","px":22.0}}""")

    /** A FontSizeAdjust declaration with the given verbatim payload. */
    private fun adjust(json: String) = prop("FontSizeAdjust", json)

    /** The used glyph size (sp == px in this runtime) the leaf Text receives. */
    private fun usedSp(vararg props: IRProperty): Float =
        TextStyleApplier.extractTextStyle(props.toList()).fontSize.value

    // ── the shared twin payload ────────────────────────────────────────────

    @Test
    fun `cap-height 0_7 on 28px is the iOS twin's 26_94`() {
        // Swift feeds {"px":28} + metric-value cap-height 0.7 and asserts
        // 26.94 ± 0.6 (it reads UIFont live); Compose's table is exact.
        val adjustProp = adjust("""{"type":"metric-value","metric":"cap-height","value":0.7}""")
        assertEquals(26.9401f, usedSp(prop("FontSize", """{"px":28}"""), adjustProp), 0.001f)
        // The factor itself: number / Inter cap-height ratio.
        assertEquals(0.7f / FontSizeAdjust.INTER_CAP_HEIGHT_EM, FontSizeAdjust.factor(listOf(adjustProp))!!, 1e-6f)
    }

    // ── the fixture's eight variants ───────────────────────────────────────

    @Test
    fun `ex-height and the bare number agree - the one-value grammar is ex-height`() {
        // FontSizeAdjust_ExHeight and FontSizeAdjust_Number (`0.5`) — §2.6:
        // a lone <number> is the ex-height aspect value.
        assertEquals(20.1503f, usedSp(fontSize22, adjust("""{"type":"metric-value","metric":"ex-height","value":0.5}""")), 0.001f)
        assertEquals(20.1503f, usedSp(fontSize22, adjust("""{"type":"number","value":0.5}""")), 0.001f)
    }

    @Test
    fun `cap-height on the fixture's 22px`() {
        // FontSizeAdjust_CapHeight (`cap-height 0.7`).
        assertEquals(21.1673f, usedSp(fontSize22, adjust("""{"type":"metric-value","metric":"cap-height","value":0.7}""")), 0.001f)
    }

    @Test
    fun `none and from-font leave the size untouched`() {
        // FontSizeAdjust_None / FontSizeAdjust_FromFont: `none` is the
        // initial value; `from-font` takes the rendering face's own ratio,
        // which for that face is exactly ×1.
        assertEquals(22f, usedSp(fontSize22, adjust("""{"type":"none"}""")), 0f)
        assertEquals(22f, usedSp(fontSize22, adjust("""{"type":"from-font"}""")), 0f)
        // The Swift pin's legacy keyword primitive spelling.
        assertEquals(22f, usedSp(fontSize22, adjust("\"NONE\"")), 0f)
        assertNull(FontSizeAdjust.factorOf(j("""{"type":"none"}""")))
    }

    @Test
    fun `advance metrics are refused loudly`() {
        // FontSizeAdjust_ChWidth / _IcWidth / _IcHeight: this runtime reads
        // no advance metrics, so the size stays and the tracker records why
        // (the no-silent-fallthrough contract; iOS skips these too).
        PropertyTracker.reset()
        val cases = listOf(
            "ch-width" to """{"type":"metric-value","metric":"ch-width","value":0.42}""",
            "ic-width" to """{"type":"metric-value","metric":"ic-width","value":1.0}""",
            "ic-height" to """{"type":"metric-value","metric":"ic-height","value":1.0}""",
        )
        for ((metric, json) in cases) {
            assertEquals(22f, usedSp(fontSize22, adjust(json)), 0f)
            assertTrue("breadcrumb for $metric", PropertyTracker.isUnhandled("FontSizeAdjust[metric:$metric]"))
        }
        PropertyTracker.reset()
    }

    @Test
    fun `non-positive numbers are refused loudly`() {
        // <number [0,∞]> admits 0, but a 0 used size renders nothing — the
        // Swift twin guards v > 0 the same way; Compose says so.
        PropertyTracker.reset()
        assertEquals(22f, usedSp(fontSize22, adjust("""{"type":"number","value":0}""")), 0f)
        assertTrue(PropertyTracker.isUnhandled("FontSizeAdjust[non-positive:0.0]"))
        PropertyTracker.reset()
    }

    // ── bases and ordering ────────────────────────────────────────────────

    @Test
    fun `no declared font-size scales the 16px default`() {
        // Swift: `(agg.fontSizePx ?? 16) * factor` — the browser default is
        // what gets adjusted when the element declares no size.
        assertEquals(14.6547f, usedSp(adjust("""{"type":"number","value":0.5}""")), 0.001f)
    }

    @Test
    fun `the sizing em basis stays the computed size`() {
        // TypographyExtractor feeds StyleApplier.buildSpacingContext (the em
        // / ch basis for sizing units); css-values-4 §6.1.1 em tracks the
        // COMPUTED font-size, so the adjust must not reach this config —
        // the same order the Swift twin keeps (em resolved before the scale).
        val cfg = TypographyExtractor.extractTypographyConfig(
            listOf(
                "FontSize" to fontSize22.data,
                "FontSizeAdjust" to j("""{"type":"metric-value","metric":"cap-height","value":0.7}"""),
            )
        )
        assertEquals(22f.sp, cfg.fontSize)
    }

    @Test
    fun `last declaration wins like the cascade`() {
        // A later `none` cancels; a later metric adjusts.
        val cap = adjust("""{"type":"metric-value","metric":"cap-height","value":0.7}""")
        val none = adjust("""{"type":"none"}""")
        assertEquals(22f, usedSp(fontSize22, cap, none), 0f)
        assertEquals(21.1673f, usedSp(fontSize22, none, cap), 0.001f)
    }

    @Test
    fun `absent property is byte-identical - no adjustment and no factor`() {
        // The whole wave49-final corpus takes this path (zero carriers).
        assertEquals(22f, usedSp(fontSize22), 0f)
        assertNull(FontSizeAdjust.factor(listOf(fontSize22)))
        // And the rest of the style is untouched by the hook.
        val style = TextStyleApplier.extractTextStyle(listOf(fontSize22, prop("TextOverflow", "\"ellipsis\"")))
        assertEquals(22f.sp, style.fontSize)
        assertTrue(TextOverflow.Ellipsis == TextStyleApplier.extractTextOverflow(listOf(prop("TextOverflow", "\"ellipsis\""))))
    }
}
