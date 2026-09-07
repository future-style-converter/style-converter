package com.styleconverter.runtime.spacing

// Wave-43 lane V3 — the `lh` unit's line-height source (css-values-4
// §6.1.1: lh = the element's USED line-height, not a hardcoded 1.2em).
//
// Wire shapes are copied VERBATIM from the live wave42-final IR
// (tools/titan/runs/wave42-final/sections/css-overflow/per-test-ir/
// wpt__css-overflow__line-clamp__discard__discard-multicol-001.json):
//   {"type":"FontFamily","data":["monospace"]}
//   {"type":"Height","data":{"type":"length","original":{"v":2,"u":"LH"}}}
// and from the converter's LineHeight serializer for the keyword:
//   {"multiplier":1.2,"original":"normal"}
// Never invent wire shapes — these tests pin decoder + pick + resolver
// against exactly what the converter emits.

import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.core.renderer.REF_DEFAULT_FONT_LINE_HEIGHT_RATIO
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.extractLength
import com.styleconverter.runtime.typography.LineHeightNormal
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LhUnitLineHeightTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    // ── The pure three-state pick ───────────────────────────────────────────

    @Test fun `declared typed line-height wins verbatim in both modes`() {
        // Author value beats every calibration (css-values-4 §6.1.1 — the
        // computed line-height of the element IS the lh basis).
        assertEquals(32f, LhUnitLineHeight.usedLineHeightPx(32f, false, 16f, wptCapture = true)!!, 0f)
        assertEquals(32f, LhUnitLineHeight.usedLineHeightPx(32f, false, 16f, wptCapture = false)!!, 0f)
    }

    @Test fun `declared normal takes the calibrated ratio under WPT capture only`() {
        // Under capture the keyword's extracted 1.2 stand-in must NOT pose
        // as an author value — the calibrated composed ratio applies.
        assertEquals(20f, LhUnitLineHeight.usedLineHeightPx(19.2f, true, 16f, wptCapture = true)!!, 1e-4f)
        // Outside capture the extracted 1.2 stand-in wins as DECLARED —
        // numerically the SAME 19.2 the legacy fallback computed, so the
        // dark stage cannot move (LineHeightNormal's "elsewhere" column).
        assertEquals(19.2f, LhUnitLineHeight.usedLineHeightPx(19.2f, true, 16f, wptCapture = false)!!, 1e-4f)
    }

    @Test fun `absent line-height calibrates under capture and stays null outside`() {
        // Bare text under WPT capture lays on the pinned composed grid
        // (1.25 × font — capture-browser-ref.mjs REF_LINE_HEIGHT), so lh
        // must measure that same grid.
        assertEquals(16f * REF_DEFAULT_FONT_LINE_HEIGHT_RATIO,
            LhUnitLineHeight.usedLineHeightPx(null, false, 16f, wptCapture = true)!!, 1e-4f)
        // Outside capture: null — the resolver keeps its historical
        // 1.2 × font-size arm and every committed baseline is untouched.
        assertNull(LhUnitLineHeight.usedLineHeightPx(null, false, 16f, wptCapture = false))
    }

    // ── The resolver consumes the threaded channel ──────────────────────────

    @Test fun `lh resolves against the threaded used line-height`() {
        // 2lh on a 16.25px used line-height = 32.5 (the ref's content box
        // for discard-multicol-001 — 2 × 13 × 1.25).
        val v = LengthValue.Relative(2.0, LengthUnit.LH, null)
        val ctx = SpacingContext(fontSizePx = 13f, lineHeightPx = 16.25f)
        assertEquals(32.5f, resolveToDp(v, ctx).value, 1e-4f)
    }

    @Test fun `lh keeps the historical 1_2em fallback when no channel is threaded`() {
        // The dark-stage byte-stability pin: a default context (null
        // channel) resolves exactly as wave 42 did — 2 × 1.2 × 13 = 31.2.
        val v = LengthValue.Relative(2.0, LengthUnit.LH, null)
        assertEquals(31.2f, resolveToDp(v, SpacingContext(fontSizePx = 13f)).value, 1e-4f)
    }

    @Test fun `calc lh stays in lockstep with the bare unit`() {
        // The file rule: calc(1lh + 2px) and 1lh + 2px must agree — both
        // read the same threaded channel (16.25 + 2 = 18.25)…
        val ctx = SpacingContext(fontSizePx = 13f, lineHeightPx = 16.25f)
        assertEquals(18.25f, evalCalc("calc(1lh + 2px)", ctx), 1e-4f)
        // …and both fall back to 1.2em together when it is null.
        assertEquals(17.6f, evalCalc("calc(1lh + 2px)", SpacingContext(fontSizePx = 13f)), 1e-4f)
    }

    @Test fun `rlh is deliberately unmoved by the lh channel`() {
        // No capture exercises rlh and the root is never styled — the 1.2
        // root ratio stays pinned even when an element channel is present.
        val v = LengthValue.Relative(1.0, LengthUnit.RLH, null)
        val ctx = SpacingContext(fontSizePx = 13f, lineHeightPx = 16.25f)
        assertEquals(19.2f, resolveToDp(v, ctx).value, 1e-4f)
    }

    // ── End-to-end on the discard-multicol-001 verbatim wire ────────────────

    @Test fun `discard-multicol-001 wire resolves height 2lh to the ref geometry under capture`() {
        // The component's typed properties exactly as the converter emitted
        // them (FontFamily monospace, NO FontSize, NO LineHeight, Height 2lh).
        val pairs = listOf<Pair<String, kotlinx.serialization.json.JsonElement?>>(
            "FontFamily" to parse("""["monospace"]"""),
            "Height" to parse("""{"type":"length","original":{"v":2,"u":"LH"}}"""),
        )
        // The Height payload decodes to the LH relative the resolver sees.
        val h = extractLength(pairs[1].second)
        assertEquals(LengthValue.Relative(2.0, LengthUnit.LH, null), h)
        // Extraction folds the monospace UA quirk: fontSize 13sp, and the
        // WPT-capture context must carry lh = 13 × 1.25 = 16.25.
        val config = StyleApplier.extractConfig(pairs, wptCaptureMode = true)
        val ctx = StyleApplier.buildSpacingContext(
            config, wptCaptureMode = true, lineHeightDeclaredNormal = false)
        assertEquals(13f, ctx.fontSizePx, 0f)
        assertEquals(16.25f, ctx.lineHeightPx!!, 1e-4f)
        // height: 2lh → 32.5px content — the ref's measured 33px box
        // (rows 88..122 minus the 1px borders), against the wave-42
        // resolution of 31.2 (2 × 1.2 × 13) that clipped the second row.
        assertEquals(32.5f, resolveToDp(h, ctx).value, 1e-4f)
        // The same wire OUTSIDE capture keeps the wave-42 number exactly —
        // the dark-stage byte-stability half of the contract.
        val darkCtx = StyleApplier.buildSpacingContext(StyleApplier.extractConfig(pairs))
        assertNull(darkCtx.lineHeightPx)
        assertEquals(31.2f, resolveToDp(h, darkCtx).value, 1e-4f)
    }

    @Test fun `declared normal wire is discriminated by the shared predicate`() {
        // The converter's verbatim keyword payload (LineHeightSerializer):
        // `original` is the bare STRING "normal" — the exact test the
        // applyProperties wiring feeds into the pick.
        assertTrue(LineHeightNormal.isDeclaredNormal(
            parse("""{"multiplier":1.2,"original":"normal"}""")))
        // A real authored number sharing the multiplier field is NOT the
        // keyword (object-typed `original`) — it must keep DECLARED.
        org.junit.Assert.assertFalse(LineHeightNormal.isDeclaredNormal(
            parse("""{"multiplier":1.2,"original":{"type":"number","value":1.2}}""")))
    }
}
