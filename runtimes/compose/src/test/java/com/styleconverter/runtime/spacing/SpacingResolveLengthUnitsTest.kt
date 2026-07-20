package com.styleconverter.runtime.spacing

// Wave-18 lane 2 — length-unit fidelity pins (the cross-platform pin table
// P1–P12; the iOS twin lives in LengthUnitFidelityTests.swift).
//
// Wire shapes are copied VERBATIM from the live wave18-gate IRs:
//   * ch width — tools/titan/runs/wave18-gate/sections/css-overflow/
//     per-test-ir/wpt__css-overflow__block-ellipsis-001.json:
//       {"type":"Width","data":{"type":"length","original":{"v":63.1,"u":"CH"}}}
//   * bare-number percent padding/margin — …/css-sizing/per-test-ir/
//     wpt__css-sizing__abspos-auto-sizing-fit-content-percentage-003.json:
//       {"type":"PaddingLeft","data":50}
// Never invent wire shapes — these tests pin the decoder + resolver against
// exactly what the converter emits.

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.extractLength
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacingResolveLengthUnitsTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    // Default context: fontSize 16, viewport 390×844, no parent width.
    private val ctx = SpacingContext()

    // ── Wire-shape pins (live IR, never invented) ───────────────────────────

    @Test fun `ch width decodes to Relative CH from the live block-ellipsis wire`() {
        // The EXACT data payload the converter emits for `width: 63.1ch`.
        val v = extractLength(parse("""{"type":"length","original":{"v":63.1,"u":"CH"}}"""))
        assertEquals(LengthValue.Relative(63.1, LengthUnit.CH, null), v)
    }

    @Test fun `bare-number padding decodes to Relative PERCENT (abspos-003 wire)`() {
        // `padding-left: 50%` ships as the bare number 50 on the wire.
        val v = extractLength(parse("50"))
        assertEquals(LengthValue.Relative(50.0, LengthUnit.PERCENT, null), v)
    }

    // ── P1: ch — measured advance, spec 0.5em fallback ──────────────────────

    @Test fun `ch falls back to half an em when metrics are unavailable`() {
        // css-values-4 §6.1.3: '0' is "assumed to be 0.5em wide" → 63.1 × 8.
        // Previously this collapsed to 0 (the silent else-arm), rendering
        // the whole block-ellipsis-001 component blank.
        val v = LengthValue.Relative(63.1, LengthUnit.CH, null)
        assertEquals(504.8f, resolveToDp(v, ctx).value, 1e-3f)
    }

    @Test fun `ch uses the measured advance of zero when provided`() {
        // Monospace '0' at 16px measures ≈9.6px on the device; the resolver
        // must multiply the measured advance, not an em approximation.
        val v = LengthValue.Relative(63.1, LengthUnit.CH, null)
        val measured = ctx.copy(chAdvancePx = 9.6f)
        assertEquals(63.1f * 9.6f, resolveToDp(v, measured).value, 1e-3f)
    }

    @Test fun `ChUnitMetrics returns null on the JVM and never zero`() {
        // Pure-JVM tests have no android.graphics runtime — measure() must
        // report "unavailable" (null), NOT 0, so the resolver takes the
        // spec fallback instead of collapsing the box.
        org.junit.Assert.assertNull(ChUnitMetrics.measure(null, 16f))
    }

    // ── P2–P6: the font-relative fallback constants ─────────────────────────

    @Test fun `ex resolves to half an em per the spec fallback`() {
        // §6.1.3: x-height "assumed to be 0.5em" when indeterminable.
        val v = LengthValue.Relative(4.0, LengthUnit.EX, null)
        assertEquals(4f * 8f, resolveToDp(v, ctx).value, 1e-4f)
    }

    @Test fun `ic and cap resolve to one em`() {
        // §6.1.3 mandates 1em for ic; cap uses the documented 1em
        // approximation (spec fallback is the ascent — see resolver docs).
        assertEquals(32f, resolveToDp(LengthValue.Relative(2.0, LengthUnit.IC, null), ctx).value, 1e-4f)
        assertEquals(32f, resolveToDp(LengthValue.Relative(2.0, LengthUnit.CAP, null), ctx).value, 1e-4f)
    }

    @Test fun `lh and rlh resolve to 1_2 times the font sizes`() {
        // `normal` line-height ≈ 1.2 × font-size (UA default sheets).
        assertEquals(19.2f, resolveToDp(LengthValue.Relative(1.0, LengthUnit.LH, null), ctx).value, 1e-4f)
        assertEquals(19.2f, resolveToDp(LengthValue.Relative(1.0, LengthUnit.RLH, null), ctx).value, 1e-4f)
    }

    // ── P7/P8: logical viewport axes ────────────────────────────────────────

    @Test fun `vi and vb map onto vw and vh in horizontal-tb`() {
        // 10vi = 10% of 390 = 39; 10vb = 10% of 844 = 84.4. Previously both
        // collapsed to the silent 0 arm.
        assertEquals(39f, resolveToDp(LengthValue.Relative(10.0, LengthUnit.VI, null), ctx).value, 1e-4f)
        assertEquals(84.4f, resolveToDp(LengthValue.Relative(10.0, LengthUnit.VB, null), ctx).value, 1e-3f)
    }

    // ── P9 (amended by the skeptic cross-native probe) ──────────────────────

    @Test fun `container-query units fall back to the viewport like iOS`() {
        // css-contain-3 §9: with no eligible container, cq* fall back to
        // the small viewport size — the rule the iOS resolver has always
        // applied (cqw→vw, cqh→vh). The probe caught Compose collapsing
        // cq* to 0 instead: identical IR gave 0 on Android vs 39 on iOS.
        assertEquals(39f, resolveToDp(LengthValue.Relative(10.0, LengthUnit.CQW, null), ctx).value, 1e-4f)
        assertEquals(84.4f, resolveToDp(LengthValue.Relative(10.0, LengthUnit.CQH, null), ctx).value, 1e-3f)
        assertEquals(39f, resolveToDp(LengthValue.Relative(10.0, LengthUnit.VMIN, null), ctx).value, 1e-4f)
        assertEquals(39f, resolveToDp(LengthValue.Relative(10.0, LengthUnit.CQMIN, null), ctx).value, 1e-4f)
        assertEquals(84.4f, resolveToDp(LengthValue.Relative(10.0, LengthUnit.CQMAX, null), ctx).value, 1e-3f)
    }

    @Test fun `fr and unknown units fall back to zero and are tracked`() {
        // The residual else-arm (fr / wire-drift UNKNOWN) stays tracked —
        // never silent (the durable half of pin P9).
        PropertyTracker.reset()
        val v = LengthValue.Relative(10.0, LengthUnit.UNKNOWN, null)
        assertEquals(0f, resolveToDp(v, ctx).value, 0f)
        assertTrue(PropertyTracker.isUnhandled("SpacingResolve:UNKNOWN"))
        PropertyTracker.reset()
    }

    // ── P10/P11/P12: the percent tri-state ──────────────────────────────────

    @Test fun `percent uses a definite parent width when provided`() {
        // P10 — 50% of a 200px containing block = 100.
        val v = LengthValue.Relative(50.0, LengthUnit.PERCENT, null)
        assertEquals(100f, resolveToDp(v, ctx.copy(parentWidthPx = 200f)).value, 1e-4f)
    }

    @Test fun `percent resolves to zero when indefinite under the WPT rule`() {
        // P11 — css-position-3 §5.1 / css-sizing-3 §5.2.1: indefinite basis
        // → 0. This is what keeps `padding-left: 50%` inside a fit-content
        // abspos box at 0 instead of half a viewport.
        val v = LengthValue.Relative(50.0, LengthUnit.PERCENT, null)
        assertEquals(0f, resolveToDp(v, ctx.copy(percentIndefiniteAsZero = true)).value, 0f)
    }

    @Test fun `percent keeps the legacy viewport fallback by default`() {
        // P12 — the frozen dark-stage behaviour: 50% × 390 = 195.
        val v = LengthValue.Relative(50.0, LengthUnit.PERCENT, null)
        assertEquals(195f, resolveToDp(v, ctx).value, 1e-4f)
    }

    @Test fun `negative percent margins keep their sign through the tri-state`() {
        // abspos-001/002 wire: MarginLeft/MarginRight = -50 (bare number).
        val v = extractLength(parse("-50"))
        assertEquals(LengthValue.Relative(-50.0, LengthUnit.PERCENT, null), v)
        // Definite basis: -50% × 200 = -100 (margins may be negative).
        assertEquals(-100f, resolveToDp(v, ctx.copy(parentWidthPx = 200f)).value, 1e-4f)
        // Indefinite WPT basis: 0 — the browser-ref geometry.
        assertEquals(0f, resolveToDp(v, ctx.copy(percentIndefiniteAsZero = true)).value, 0f)
    }

    // ── calc() stays in lockstep with the unit table ────────────────────────

    @Test fun `calc resolves ch and the percent tri-state like bare units`() {
        // ch inside calc uses the same measured advance…
        assertEquals(24f, evalCalc("calc(2ch + 4px)", ctx.copy(chAdvancePx = 10f)), 1e-4f)
        // …and % inside calc obeys the same indefinite→0 WPT rule.
        assertEquals(4f, evalCalc("calc(50% + 4px)", ctx.copy(percentIndefiniteAsZero = true)), 1e-4f)
        // Legacy default: 50% × 390 + 4. (Wave-18 cleanup: the iOS
        // CalcEvaluator twin pins 50% × its own legacy 358 base — the
        // calc-% asymmetry is shared by design, see SpacingResolve.kt.)
        assertEquals(199f, evalCalc("calc(50% + 4px)", ctx), 1e-4f)
    }

    @Test fun `calc resolves the live padding wire like the iOS evaluator`() {
        // Wave-18 cleanup (skeptic B2) — the live converter wire
        // {"type":"PaddingLeft","data":{"expr":"calc(1em + 4px)"}}:
        // 16 + 4 = 20 on BOTH natives (iOS pin: Wave18CleanupParityTests).
        assertEquals(20f, evalCalc("calc(1em + 4px)", ctx), 1e-4f)
        // And the width wire calc(2ch + 4px) under the unmeasured 0.5em
        // ch fallback: 2×8 + 4 = 20 — the value the skeptic probe saw
        // Compose render while iOS dropped to 0.
        assertEquals(20f, evalCalc("calc(2ch + 4px)", ctx), 1e-4f)
    }

    // ── The applier gates (identity for the frozen corpus) ──────────────────

    @Test fun `percent and ch gates fire only on their own unit`() {
        val pct = LengthValue.Relative(50.0, LengthUnit.PERCENT, null)
        val ch = LengthValue.Relative(2.0, LengthUnit.CH, null)
        val px = LengthValue.Exact(20.0)
        // The composed-percent lane must NOT trigger for px/ch configs —
        // that is the byte-stability gate for committed baselines.
        assertTrue(usesContainingBlockPercent(px, pct))
        assertFalse(usesContainingBlockPercent(px, ch, null))
        // The ch-metrics gate mirrors it for the measurement cost.
        assertTrue(usesChUnit(ch))
        assertFalse(usesChUnit(px, pct, null))
    }
}
