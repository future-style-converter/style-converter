package com.styleconverter.runtime.sizing

// Retro R2 (A4#7) — pins the css-ui-3 §2.1 border-box floor
// (sizing/BorderBoxFloor.kt), the Compose twin of
// runtimes/swiftui/Tests/StyleConverterRuntimeTests/BorderBoxFloorTests.swift
// test-for-test.
//
// ── What is being protected ────────────────────────────────────────────────
// css-ui/box-sizing-026: `box-sizing: border-box; border: 50px green solid;
// width: 10px; height: 10px` is a 100×100 green square in every browser (the
// content box floors at zero, the border box cannot shrink below its bands).
// Android took the 10px literally and let the `z-index: -1` red square behind
// it show through — wave49-final android-ref 0.9966, colorFailed as the sole
// gate failure (iOS fixed the identical shape at wave 40).
//
// ── The invariant that must never break ────────────────────────────────────
// The floor is armed by an EXPLICIT `box-sizing: border-box` ONLY. On the
// dark stage an ABSENT box-sizing is the border-box status quo the 327-pair
// baselines are captured against — flooring it would inflate every
// declared-inside-its-own-border box (the css-grid/css-flexbox abspos
// families); under WPT capture an absent box-sizing resolves to CONTENT_BOX
// and takes the inflation path instead. Both are pinned inert here.

import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BorderBoxFloorTest {

    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    /**
     * box-sizing-026's `#test` declarations, VERBATIM from
     * tools/titan/runs/wave49-final/sections/css-ui/per-test-ir/
     * wpt__css-ui__box-sizing-026.json (component __2-378).
     */
    private fun box026(boxSizing: String?): List<Pair<String, JsonElement?>> {
        val pairs = mutableListOf<Pair<String, JsonElement?>>()
        if (boxSizing != null) pairs += pair("BoxSizing", "\"$boxSizing\"")
        for (side in listOf("Top", "Right", "Bottom", "Left")) {
            pairs += pair("Border${side}Width", """{"px":50}""")
            pairs += pair("Border${side}Style", "\"SOLID\"")
            pairs += pair("Border${side}Color",
                """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}""")
        }
        pairs += pair("Width", """{"type":"length","px":10}""")
        pairs += pair("Height", """{"type":"length","px":10}""")
        return pairs
    }

    private fun exactPx(v: LengthValue?): Double? = (v as? LengthValue.Exact)?.px

    // ── 1. The per-axis arithmetic (twin of the Swift MARK 1 block) ────────

    @Test fun `under-band size floors to the band sum`() {
        assertEquals(100.0, exactPx(BorderBoxFloor.floored(LengthValue.Exact(10.0), 100f)))
    }

    @Test fun `over-band size is untouched`() {
        assertEquals(200.0, exactPx(BorderBoxFloor.floored(LengthValue.Exact(200.0), 100f)))
        // Exactly equal is not "smaller" — no change, no rounding wobble.
        assertEquals(100.0, exactPx(BorderBoxFloor.floored(LengthValue.Exact(100.0), 100f)))
    }

    @Test fun `zero band is identity`() {
        assertEquals(10.0, exactPx(BorderBoxFloor.floored(LengthValue.Exact(10.0), 0f)))
    }

    /** Only a DEFINITE px declaration is comparable — a percent/auto/null
     *  axis resolves later against a basis this helper never sees. */
    @Test fun `indefinite axes pass through verbatim`() {
        assertEquals(LengthValue.Auto, BorderBoxFloor.floored(LengthValue.Auto, 100f))
        val pct = LengthValue.Relative(50.0, LengthUnit.PERCENT, null)
        assertEquals(pct, BorderBoxFloor.floored(pct, 100f))
        assertNull(BorderBoxFloor.floored(null, 100f))
    }

    // ── 2. The trigger scope, end to end through SizingExtractor ───────────

    /** box-sizing-026 itself: 10px declared, 100px of border per axis, 100 used. */
    @Test fun `explicit border-box floors through the extractor in both capture modes`() {
        for (wpt in listOf(true, false)) {
            val cfg = SizingExtractor.extractSizingConfig(box026("BORDER_BOX"), wptCaptureMode = wpt)
            assertEquals("wpt=$wpt width", 100.0, exactPx(cfg.width))
            assertEquals("wpt=$wpt height", 100.0, exactPx(cfg.height))
            // The floor never borrows the content-box inflation bands.
            assertEquals(0f, cfg.contentBoxInflateX, 0f)
        }
    }

    /** THE dark-stage regression sentinel: an ABSENT box-sizing must NOT
     *  floor — null is the border-box status quo of the 327-pair baselines. */
    @Test fun `absent box-sizing does not floor on the dark stage`() {
        val cfg = SizingExtractor.extractSizingConfig(box026(null), wptCaptureMode = false)
        assertNull(cfg.boxSizing)
        assertEquals(10.0, exactPx(cfg.width))
        assertEquals(10.0, exactPx(cfg.height))
    }

    /** Under WPT capture an absent box-sizing is CONTENT_BOX (the UA default)
     *  — the declared 10px stays the CONTENT size and the Applier inflates by
     *  the 100px band; the floor must not touch it (that would double-count). */
    @Test fun `absent box-sizing under wpt capture takes the inflation path, not the floor`() {
        val cfg = SizingExtractor.extractSizingConfig(box026(null), wptCaptureMode = true)
        assertEquals(BoxSizingKeyword.CONTENT_BOX, cfg.boxSizing)
        assertEquals(10.0, exactPx(cfg.width))
        assertEquals(100f, cfg.contentBoxInflateX, 1e-4f)
        assertEquals(100f, cfg.contentBoxInflateY, 1e-4f)
    }

    /** An explicit content-box keeps its own inflation path and is not floored on top. */
    @Test fun `explicit content-box does not floor`() {
        val cfg = SizingExtractor.extractSizingConfig(box026("CONTENT_BOX"))
        assertEquals(10.0, exactPx(cfg.width))
        assertEquals(10.0, exactPx(cfg.height))
        assertEquals(100f, cfg.contentBoxInflateX, 1e-4f)
    }

    /** A `border-style: none` side has USED width 0 (CSS 2.1 §8.5.3), so it
     *  contributes no band and cannot floor anything — the same gate the
     *  content-box inflation uses, which is why both read one helper. */
    @Test fun `style-none borders contribute no band`() {
        val pairs = mutableListOf(pair("BoxSizing", "\"BORDER_BOX\""))
        for (side in listOf("Top", "Right", "Bottom", "Left")) {
            pairs += pair("Border${side}Width", """{"px":50}""")
            pairs += pair("Border${side}Style", "\"NONE\"")
        }
        pairs += pair("Width", """{"type":"length","px":10}""")
        val cfg = SizingExtractor.extractSizingConfig(pairs)
        assertEquals(10.0, exactPx(cfg.width))
    }

    /** Padding is part of the band too (§2.1: "border and padding widths"). */
    @Test fun `padding alone floors an under-band border-box size`() {
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("BoxSizing", "\"BORDER_BOX\""),
            pair("PaddingLeft", """{"px":30}"""), pair("PaddingRight", """{"px":30}"""),
            pair("Width", """{"type":"length","px":20}"""),
            pair("Height", """{"type":"length","px":20}"""),
        ))
        assertEquals(60.0, exactPx(cfg.width))
        // No vertical band → the height axis is untouched.
        assertEquals(20.0, exactPx(cfg.height))
    }

    /** The lane-BX sentinel stays: border-box ABOVE its band is untouched. */
    @Test fun `border-box above the band is untouched - V1_border sentinel`() {
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("BoxSizing", "\"BORDER_BOX\""),
            pair("Width", """{"type":"length","px":180.0}"""),
            pair("PaddingTop", """{"px":10.0}"""),
        ))
        assertEquals(180.0, exactPx(cfg.width))
    }
}
