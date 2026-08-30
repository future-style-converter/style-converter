//
//  GradientStopResolverTests.swift
//  Wave 46, lane Y2 — css-images natives.
//
//  Pins the css-images-4 §3.4.3 stop pipeline (GradientStopResolver),
//  the css-color-4 §12 interpolation (GradientRamp + GradientColorMath)
//  and the `positionLength` / `interp` wire reads — the mechanisms
//  behind the wave-45 iOS fails gradient-move-stops (0.81),
//  gradient-border-box / gradient-content-box (0.645), the hsl
//  increasing/decreasing-hue pair (0.92) and the powerless-hue family.
//  The byte-stability invariant every committed gradient baseline
//  relies on is pinned first: clause-less, all-nil stop lists must come
//  out of the new pipeline exactly as the wave-5 `i/(n−1)` spread +
//  12-segment sRGB subdivision produced them.
//

import XCTest
@testable import StyleConverterRuntime

final class GradientStopResolverTests: XCTestCase {

    // MARK: - Helpers

    private func stop(_ r: Double, _ g: Double, _ b: Double, a: Double = 1,
                      pos: Double? = nil, px: Double? = nil,
                      interp: GradientInterpolation = .legacy) -> BackgroundImageStop {
        BackgroundImageStop(color: .srgb(r: r, g: g, b: b, a: a), position: pos,
                            positionPx: px, interp: interp)
    }

    private func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private func srgb(_ r: Double, _ g: Double, _ b: Double, _ a: Double? = nil) -> IRValue {
        var d: [String: IRValue] = ["r": .double(r), "g": .double(g), "b": .double(b)]
        if let a = a { d["a"] = .double(a) }
        return obj(["srgb": obj(d), "original": .string("t")])
    }

    // MARK: - Byte stability of the legacy path

    func testAllNilStopsKeepTheWave5EvenSpread() {
        // 4 nil stops → 0, 1/3, 2/3, 1 — bit-identical to Double(i)/Double(3).
        let stops = [stop(1, 0, 0), stop(0, 1, 0), stop(0, 0, 1), stop(1, 1, 1)]
        let fixed = GradientStopResolver.fixup(stops, lengthPx: nil)
        for i in 0..<4 { XCTAssertEqual(fixed[i].loc, Double(i) / Double(3)) }
        // And the full ramp equals the old srgbSubdivided(resolveStops) chain.
        let new = GradientApplier.resolvedRamp(stops, lengthPx: 120, repeating: false)
        let old = GradientApplier.srgbSubdivided(fixed, segments: 12)
        XCTAssertEqual(new, old)
        XCTAssertEqual(new.count, 1 + 3 * 12)
    }

    func testDeclaredInOrderPercentsAreUntouched() {
        let fixed = GradientStopResolver.fixup(
            [stop(1, 0, 0, pos: 0), stop(0, 1, 0, pos: 0.5), stop(0, 0, 1, pos: 1)], lengthPx: nil)
        XCTAssertEqual(fixed.map { $0.loc }, [0, 0.5, 1])
    }

    // MARK: - §3.4.3 fixup

    func testStopBehindItsPredecessorClampsForward() {
        // WPT gradient-move-stops: `yellow, blue 70%, green 0` → green at 70%.
        let fixed = GradientStopResolver.fixup(
            [stop(1, 1, 0), stop(0, 0, 1, pos: 0.7), stop(0, 0.5, 0, pos: 0)], lengthPx: nil)
        XCTAssertEqual(fixed.map { $0.loc }, [0, 0.7, 0.7])
    }

    func testNilRunSpreadsBetweenPositionedNeighbours() {
        // `a 20%, b, c, d 80%` → b 40%, c 60% (not the old 1/3, 2/3).
        let fixed = GradientStopResolver.fixup(
            [stop(1, 0, 0, pos: 0.2), stop(0, 1, 0), stop(0, 0, 1), stop(1, 1, 1, pos: 0.8)], lengthPx: nil)
        XCTAssertEqual(fixed[1].loc, 0.4, accuracy: 1e-12)
        XCTAssertEqual(fixed[2].loc, 0.6, accuracy: 1e-12)
    }

    func testLengthStopsResolveAgainstTheGradientLine() {
        // gradient-border-box: `white, black, white 30px` on a 280×280
        // box at 135deg → line = 280·(sin45+cos45) ≈ 395.98px.
        let len = GradientApplier.lineLengthPx(angleDeg: 135, size: CGSize(width: 280, height: 280))
        XCTAssertEqual(len, 280 * 2.0.squareRoot(), accuracy: 1e-9)
        let fixed = GradientStopResolver.fixup(
            [stop(1, 1, 1), stop(0, 0, 0), stop(1, 1, 1, px: 30)], lengthPx: len)
        XCTAssertEqual(fixed[2].loc, 30 / len, accuracy: 1e-12)
        XCTAssertEqual(fixed[1].loc, 15 / len, accuracy: 1e-12)
    }

    func testLengthStopWithoutALineIsUnpositioned() {
        // Conic callers pass nil — the px stop degrades to the even spread.
        let fixed = GradientStopResolver.fixup(
            [stop(1, 0, 0), stop(0, 1, 0, px: 30), stop(0, 0, 1)], lengthPx: nil)
        XCTAssertEqual(fixed[1].loc, 0.5)
    }

    // MARK: - Repeating + clip

    func testRepeatingExpansionTilesThePeriodAcrossTheLine() {
        // Period 0.1 from stops at 0 / 0.05 / 0.1 → the ramp covers
        // [0, 1] with white at every multiple of 0.1 and black halfway.
        let base = GradientStopResolver.fixup(
            [stop(1, 1, 1), stop(0, 0, 0), stop(1, 1, 1, pos: 0.1)], lengthPx: nil)
        let tiled = GradientStopResolver.clipToUnit(
            GradientStopResolver.expandRepeating(base), interp: .legacy)
        XCTAssertEqual(tiled.first?.loc, 0)
        XCTAssertEqual(tiled.last?.loc, 1)
        // White stops at k·0.1 …
        let whites = tiled.filter { $0.r == 1 && $0.g == 1 && $0.b == 1 }.map { $0.loc }
        XCTAssertTrue(whites.contains { abs($0 - 0.3) < 1e-9 })
        XCTAssertTrue(whites.contains { abs($0 - 0.7) < 1e-9 })
        // … black stops at k·0.1 + 0.05.
        let blacks = tiled.filter { $0.r == 0 && $0.g == 0 && $0.b == 0 }.map { $0.loc }
        XCTAssertTrue(blacks.contains { abs($0 - 0.45) < 1e-9 })
        XCTAssertFalse(blacks.contains { abs($0 - 0.4) < 1e-9 })
        // Locations are monotonic for SwiftUI.
        XCTAssertEqual(tiled.map { $0.loc }, tiled.map { $0.loc }.sorted())
    }

    func testRepeatingLatticeStaysMonotonicDespiteOneUlpFloatDrift() {
        // Wave 48, lane F3 (S4 defect 1) — the twin of Kotlin's wave-47
        // monotonicity pin (GradientStopResolverTest.kt, the sorted-locs
        // assert): copy k's LAST stop and copy k+1's FIRST stop compute
        // through two different Double paths (`last.loc + k·p` vs
        // `first.loc + (k+1)·p`) that can land one ulp apart in either
        // order. S4's executed sweep misordered 6023 of 23275 rational
        // lattices; this shape — period 1/5, anchor 0.13 — emits
        // …0.93000000000000016 then 0.93000000000000005 (and again at
        // 1.13) WITHOUT the clamp, so this pin fails on the pre-fix
        // expansion (prove-can-fail: drop the clamp loop and watch the
        // sorted-equality assert below break).
        let base = GradientStopResolver.fixup(
            [stop(0, 1, 0, pos: 0.13), stop(1, 0, 0, pos: 0.13 + 1.0 / 5.0)], lengthPx: nil)
        let expanded = GradientStopResolver.expandRepeating(base)
        // The raw materialised lattice must already be non-decreasing —
        // SwiftUI's Gradient treats unordered stop locations as
        // undefined input, and the clip step must never see them either.
        XCTAssertEqual(expanded.map { $0.loc }, expanded.map { $0.loc }.sorted(),
                       "expandRepeating handed SwiftUI a non-monotonic lattice")
        // And the clamp is a one-ulp repair, not a reshuffle: the clipped
        // ramp still spans the whole line with the expected stop count.
        let tiled = GradientStopResolver.clipToUnit(expanded, interp: .legacy)
        XCTAssertEqual(tiled.first?.loc, 0)
        XCTAssertEqual(tiled.last?.loc, 1)
        XCTAssertEqual(tiled.map { $0.loc }, tiled.map { $0.loc }.sorted())
    }

    func testZeroPeriodRepeatingIsTheAverageSolid() {
        let base = GradientStopResolver.fixup(
            [stop(1, 0, 0, pos: 0.5), stop(0, 0, 1, pos: 0.5)], lengthPx: nil)
        let out = GradientStopResolver.expandRepeating(base)
        XCTAssertEqual(out.count, 2)
        XCTAssertEqual(out[0].r, 0.5, accuracy: 1e-12)
        XCTAssertEqual(out[0].b, 0.5, accuracy: 1e-12)
        XCTAssertEqual(out.map { $0.loc }, [0, 1])
    }

    func testPeriodTooFineForTheCapPaintsTheAverageSolidNotATruncatedLattice() {
        // `repeating-linear-gradient(white, black, white 1px)` on the
        // 390px capture line: 393 copies, past maxCopies (256). The first
        // wave-46 cut TRUNCATED the lattice to copies 0…255 — stripes on
        // the top ~256px, flat white below, a hard seam at the boundary.
        // §3.3.3's degenerate solid is the honest render instead.
        let base = GradientStopResolver.fixup(
            [stop(1, 1, 1), stop(0, 0, 0), stop(1, 1, 1, px: 1)], lengthPx: 390)
        let out = GradientStopResolver.expandRepeating(base)
        // Two stops, both ends of the line — never a partial lattice.
        XCTAssertEqual(out.count, 2)
        XCTAssertEqual(out.map { $0.loc }, [0, 1])
        // Average of white, black, white = 2/3 on every channel, and the
        // SAME colour at both ends, so the fill is uniform.
        for s in out {
            XCTAssertEqual(s.r, 2.0 / 3.0, accuracy: 1e-12)
            XCTAssertEqual(s.g, 2.0 / 3.0, accuracy: 1e-12)
            XCTAssertEqual(s.b, 2.0 / 3.0, accuracy: 1e-12)
            XCTAssertEqual(s.a, 1)
        }
        // End to end (clip + subdivision): every stop of the rendered
        // ramp is that one colour — no seam anywhere on the line.
        let ramp = GradientApplier.resolvedRamp(
            [stop(1, 1, 1), stop(0, 0, 0), stop(1, 1, 1, px: 1)], lengthPx: 390, repeating: true)
        XCTAssertFalse(ramp.isEmpty)
        XCTAssertTrue(ramp.allSatisfy { abs($0.r - 2.0 / 3.0) < 1e-9 && $0.r == $0.g && $0.g == $0.b })
    }

    func testThirtyPxPeriodStillMaterialisesTheWholeLattice() {
        // The gradient-border-box period on the same 390px line: 13 whole
        // periods, ~16 copies — far under the cap, so the degrade branch
        // must not fire and the lattice is unchanged.
        let base = GradientStopResolver.fixup(
            [stop(1, 1, 1), stop(0, 0, 0), stop(1, 1, 1, px: 30)], lengthPx: 390)
        let tiled = GradientStopResolver.clipToUnit(
            GradientStopResolver.expandRepeating(base), interp: .legacy)
        XCTAssertEqual(tiled.first?.loc, 0)
        XCTAssertEqual(tiled.last?.loc, 1)
        let period = 30.0 / 390.0
        // White at every k·period, black halfway between — across the
        // WHOLE line, including the far end a truncation would flatten.
        for k in 1...12 {
            XCTAssertTrue(tiled.contains { abs($0.loc - Double(k) * period) < 1e-9 && $0.r == 1 && $0.b == 1 },
                          "missing white stop at k=\(k)")
            XCTAssertTrue(tiled.contains { abs($0.loc - (Double(k) + 0.5) * period) < 1e-9 && $0.r == 0 && $0.b == 0 },
                          "missing black stop at k=\(k)+½")
        }
        // Monotonic for SwiftUI, and emphatically not the 2-stop solid.
        XCTAssertEqual(tiled.map { $0.loc }, tiled.map { $0.loc }.sorted())
        XCTAssertGreaterThan(tiled.count, 3 * 13)
    }

    func testSingleStopRepeatingStaysASolidFill() {
        // gradient-single-stop-003: `repeating-linear-gradient(green 50px)`.
        let ramp = GradientApplier.resolvedRamp([stop(0, 0.5, 0, px: 50)], lengthPx: 100, repeating: true)
        XCTAssertEqual(ramp.count, 1)
        XCTAssertEqual(ramp[0].g, 0.5)
    }

    func testStopsBeyondTheLineClipToTheBoundaryColour() {
        // gradient-infinity-001 shape: `lime 100px, red calc(∞)` on a 100px
        // line → lime to the end; the red stop never enters the box.
        let base = GradientStopResolver.fixup(
            [stop(0, 1, 0, px: 100), stop(1, 0, 0, px: 1e9)], lengthPx: 100)
        let out = GradientStopResolver.clipToUnit(base, interp: .legacy)
        XCTAssertEqual(out.first?.loc, 0)
        XCTAssertEqual(out.last?.loc, 1)
        XCTAssertTrue(out.allSatisfy { $0.g == 1 && $0.r < 1e-6 })
    }

    // ── Wave 48 (lane W1 twin): every stop past one end of the line ─────
    // Byte-parallel pins with GradientStopResolverTest.kt: the VERBATIM
    // css-break/background-image-006 clone shape, `linear-gradient(green
    // 80px, red 140px)` on a fragment line SHORTER than 80px (the WPT ref
    // is a green square precisely because the 80px stop sits past every
    // fragment's padding box). clipToUnit used to answer [] for the
    // all-outside shape, leaving the ramp unpaintable — §3.4.3's padding
    // rule says the first stop's colour fills everything before it.

    func testAllStopsBeyondTheLineEndPaintTheFirstColourUniformly() {
        // 80/45 and 140/45 both land past 1.0 on a 45px line (a measured
        // failing fragment size from the Android device diagnosis).
        let base = GradientStopResolver.fixup(
            [stop(0, 0.5019607843137255, 0, px: 80), stop(1, 0, 0, px: 140)],
            lengthPx: 45)
        let out = GradientStopResolver.clipToUnit(base, interp: .legacy)
        // Uniform fill = the §3.4.4 two-identical-stops idiom, never [].
        XCTAssertEqual(out.count, 2)
        XCTAssertEqual(out.first?.loc, 0)
        XCTAssertEqual(out.last?.loc, 1)
        XCTAssertTrue(out.allSatisfy { $0.g == 0.5019607843137255 && $0.r < 1e-6 },
                      "the whole [0,1] range must be corpus-green")
    }

    func testAllStopsBeforeTheLineStartPaintTheLastColourUniformly() {
        // The mirrored case (negative px stops): [0,1] lies entirely after
        // the last stop, which owns the padding colour per §3.4.3.
        let base = GradientStopResolver.fixup(
            [stop(0, 0.5019607843137255, 0, px: -140), stop(1, 0, 0, px: -80)],
            lengthPx: 45)
        let out = GradientStopResolver.clipToUnit(base, interp: .legacy)
        XCTAssertEqual(out.count, 2)
        XCTAssertTrue(out.allSatisfy { $0.r == 1 && $0.g < 1e-6 },
                      "the whole [0,1] range must be corpus-red")
    }

    func testAStopExactlyAtTheLineEndKeepsTheInRangePath() {
        // The reference-intended geometry: an 80px padding box puts the
        // green stop AT 1.0 (in range) — the fix must not disturb it: a
        // green §3.4.3 pad at 0, green kept at its declared 1.0.
        let base = GradientStopResolver.fixup(
            [stop(0, 0.5019607843137255, 0, px: 80), stop(1, 0, 0, px: 140)],
            lengthPx: 80)
        let out = GradientStopResolver.clipToUnit(base, interp: .legacy)
        XCTAssertEqual(out.first?.loc, 0)
        XCTAssertEqual(out.first?.g, 0.5019607843137255)
        XCTAssertTrue(out.contains { $0.loc == 1 && $0.g == 0.5019607843137255 },
                      "green must still sit at its declared 1.0")
    }

    // MARK: - Interpolation clause

    func testInterpClauseParsing() {
        XCTAssertEqual(GradientInterpolation.parse(nil), .legacy)
        XCTAssertEqual(GradientInterpolation.parse("in oklab"),
                       GradientInterpolation(space: .oklab, hue: .shorter))
        XCTAssertEqual(GradientInterpolation.parse("in hsl longer hue"),
                       GradientInterpolation(space: .hsl, hue: .longer))
        XCTAssertEqual(GradientInterpolation.parse("in lch decreasing hue"),
                       GradientInterpolation(space: .lch, hue: .decreasing))
        // Hue method on a rectangular space is ungrammatical (§12.4).
        XCTAssertEqual(GradientInterpolation.parse("in oklab longer hue"), .legacy)
        // Unsupported-but-valid space degrades to legacy (with a breadcrumb).
        XCTAssertEqual(GradientInterpolation.parse("in display-p3-linear"), .legacy)
    }

    func testExtractorReadsPositionLengthAndInterp() {
        let cfg = BackgroundImageExtractor.extract(from: [IRProperty(type: "BackgroundImage", data: .array([
            obj(["type": .string("repeating-linear-gradient"),
                 "angle": obj(["deg": .double(135)]),
                 "interp": .string("in hsl increasing hue"),
                 "stops": .array([
                    obj(["color": srgb(1, 1, 1), "position": .null]),
                    obj(["color": srgb(1, 1, 1), "position": .null,
                         "positionLength": obj(["px": .double(30)])]),
                 ])])
        ]))])
        guard case .repeating(let kind, let angle, let stops) = cfg?.layers.first ?? .none else {
            return XCTFail("expected a repeating layer")
        }
        XCTAssertEqual(kind, .linear)
        XCTAssertEqual(angle, 135)
        XCTAssertNil(stops[0].positionPx)
        XCTAssertEqual(stops[1].positionPx, 30)
        XCTAssertEqual(stops[1].interp, GradientInterpolation(space: .hsl, hue: .increasing))
    }

    // MARK: - css-color-4 §12.5 hue arcs

    func testHueArcSelection() {
        // Examples straight from the §12.5 tables (h1 = 0, h2 = 40).
        XCTAssertTrue(GradientRamp.hueArc(0, 40, method: .shorter) == (0, 40))
        XCTAssertTrue(GradientRamp.hueArc(0, 40, method: .longer) == (360, 40))
        XCTAssertTrue(GradientRamp.hueArc(0, 40, method: .increasing) == (0, 40))
        XCTAssertTrue(GradientRamp.hueArc(0, 40, method: .decreasing) == (360, 40))
        XCTAssertTrue(GradientRamp.hueArc(40, 0, method: .increasing) == (40, 360))
        XCTAssertTrue(GradientRamp.hueArc(40, 0, method: .decreasing) == (40, 0))
        // Equal hues under `longer` sweep the full wheel.
        XCTAssertTrue(GradientRamp.hueArc(0, 0, method: .longer) == (0, 360))
        // Shorter never exceeds 180°: 350 → 10 goes through 360.
        XCTAssertTrue(GradientRamp.hueArc(350, 10, method: .shorter) == (350, 370))
    }

    // MARK: - GradientRamp

    func testHslIncreasingHueRedToPurpleSweepsThroughGreen() {
        // gradient-increasing-hue-hsl row 3: hsl(0) → hsl(270) increasing
        // → midpoint hue 135 (spring green), not the 315 the short arc gives.
        let a = GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 0)
        let b = GradientApplier.RGBAStop(r: 0.5, g: 0, b: 1, a: 1, loc: 1)
        let mid = GradientRamp.interpolate(a, b, t: 0.5,
                                           interp: GradientInterpolation(space: .hsl, hue: .increasing))
        let h = GradientColorMath.srgbToHSL(mid.r, mid.g, mid.b)
        XCTAssertEqual(h.0, 135, accuracy: 0.01)
        XCTAssertEqual(h.1, 100, accuracy: 0.01)
    }

    func testHslPremultipliedFadeKeepsSaturation() {
        // gradient-powerless-hue-hsl: red → hsl(120 100% 50% / 0) in hsl:
        // the midpoint is YELLOW at half alpha — saturation/lightness are
        // premultiplied so the transparent stop cannot wash them out.
        let a = GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 0)
        let b = GradientApplier.RGBAStop(r: 0, g: 1, b: 0, a: 0, loc: 1)
        let mid = GradientRamp.interpolate(a, b, t: 0.5,
                                           interp: GradientInterpolation(space: .hsl, hue: .shorter))
        XCTAssertEqual(mid.r, 1, accuracy: 1e-9)
        XCTAssertEqual(mid.g, 1, accuracy: 1e-9)
        XCTAssertEqual(mid.b, 0, accuracy: 1e-9)
        XCTAssertEqual(mid.a, 0.5, accuracy: 1e-12)
    }

    func testPowerlessHueIsCarriedFromTheOtherStop() {
        // gradient-longer-hue-hsl-013 row 1: red → black in hsl longer hue.
        // Black's hue is powerless, adopts red's 0, and `longer` on equal
        // hues sweeps 360° — the midpoint sits at hue 180 (cyan), l = 25.
        let a = GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 0)
        let b = GradientApplier.RGBAStop(r: 0, g: 0, b: 0, a: 1, loc: 1)
        let mid = GradientRamp.interpolate(a, b, t: 0.5,
                                           interp: GradientInterpolation(space: .hsl, hue: .longer))
        let h = GradientColorMath.srgbToHSL(mid.r, mid.g, mid.b)
        XCTAssertEqual(h.0, 180, accuracy: 0.01)
        XCTAssertEqual(h.2, 25, accuracy: 0.01)
    }

    func testLegacyInterpolationIsThePlainSrgbLerp() {
        // red → transparent, legacy: non-premultiplied (0.5, 0, 0, 0.5).
        let a = GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 0)
        let b = GradientApplier.RGBAStop(r: 0, g: 0, b: 0, a: 0, loc: 1)
        let mid = GradientRamp.interpolate(a, b, t: 0.5, interp: .legacy)
        XCTAssertEqual(mid.r, 0.5, accuracy: 1e-12)
        XCTAssertEqual(mid.a, 0.5, accuracy: 1e-12)
    }

    func testOklabMidpointOfLimeAndRedIsBrighterThanTheSrgbOlive() {
        // gradient-analogous-missing-components-004: `in oklab, lime, red`
        // passes a bright yellow-orange at the middle; sRGB gives olive.
        let a = GradientApplier.RGBAStop(r: 0, g: 1, b: 0, a: 1, loc: 0)
        let b = GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 1)
        let mid = GradientRamp.interpolate(a, b, t: 0.5,
                                           interp: GradientInterpolation(space: .oklab, hue: .shorter))
        XCTAssertGreaterThan(mid.r, 0.7)
        XCTAssertGreaterThan(mid.g, 0.6)
        XCTAssertLessThan(mid.b, 0.05)
    }

    func testLongerHueSubdivisionDensity() {
        // A 360° sweep gets ≥ 36 micro-stops (one per ≤10°), not 12.
        let a = GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 0)
        let b = GradientApplier.RGBAStop(r: 0, g: 0, b: 0, a: 1, loc: 1)
        let fine = GradientRamp.subdivided([a, b], interp: GradientInterpolation(space: .hsl, hue: .longer))
        XCTAssertGreaterThanOrEqual(fine.count, 37)
    }

    // MARK: - Browser ground truth (frozen WPT reference pixels)

    /// Composite a ramp sample over white and compare to an 8-bit pixel
    /// probed from the frozen Chromium reference PNG (tools/wpt/refs/…/
    /// css-images). Tolerance 4/255 covers the reference's own
    /// quantisation + the ±1px probe position.
    private func assertRef(_ s: GradientApplier.RGBAStop, _ rgb: (Int, Int, Int),
                           _ label: String, tolerance: Int = 4,
                           file: StaticString = #filePath, line: UInt = #line) {
        func over(_ c: Double) -> Int { Int((c * s.a + (1 - s.a)) * 255 + 0.5) }
        XCTAssertEqual(over(s.r), rgb.0, accuracy: tolerance, "\(label) r", file: file, line: line)
        XCTAssertEqual(over(s.g), rgb.1, accuracy: tolerance, "\(label) g", file: file, line: line)
        XCTAssertEqual(over(s.b), rgb.2, accuracy: tolerance, "\(label) b", file: file, line: line)
    }

    func testPowerlessHueFamilyMatchesTheChromiumReference() {
        // `linear-gradient(to right in <space>, red, <transparent green>)`
        // — the stop pair exactly as the converter emits it (sRGB floats,
        // alpha 0) for gradient-powerless-hue-{hsl,hwb,lch,oklch}; the
        // expected triples were probed from the frozen refs at x = 25/50/75%.
        let red = GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 0)
        let green = GradientApplier.RGBAStop(r: 0, g: 1, b: 0, a: 0, loc: 1)
        let lchGreen = GradientApplier.RGBAStop(r: 0.0010253138701899005, g: 1, b: 0, a: 0, loc: 1)
        let table: [(GradientInterpolation.Space, GradientApplier.RGBAStop, [(Double, (Int, Int, Int))])] = [
            (.hsl,   green,    [(0.25, (255, 161, 65)), (0.5, (255, 255, 128)), (0.75, (224, 255, 192))]),
            (.hwb,   green,    [(0.25, (255, 161, 65)), (0.5, (255, 255, 128)), (0.75, (224, 255, 192))]),
            (.lch,   lchGreen, [(0.25, (224, 133, 64)), (0.5, (207, 191, 128)), (0.75, (213, 228, 192))]),
            (.oklch, green,    [(0.25, (245, 115, 64)), (0.5, (229, 185, 128)), (0.75, (226, 229, 192))]),
        ]
        for (space, b, samples) in table {
            for (t, px) in samples {
                let s = GradientRamp.interpolate(red, b, t: t, interp: GradientInterpolation(space: space, hue: .shorter))
                assertRef(s, px, "\(space) t=\(t)")
            }
        }
    }

    func testOklabLimeToRedMatchesTheChromiumReference() {
        // gradient-analogous-missing-components-004 row 5 (`in oklab`).
        let lime = GradientApplier.RGBAStop(r: 0, g: 1, b: 0, a: 1, loc: 0)
        let red = GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 1)
        for (t, px) in [(0.25, (160, 212, 0)), (0.5, (208, 168, 0)), (0.75, (237, 114, 0))] {
            let s = GradientRamp.interpolate(lime, red, t: t, interp: GradientInterpolation(space: .oklab, hue: .shorter))
            assertRef(s, px, "oklab t=\(t)")
        }
    }

    func testLongerHueRedToBlackMatchesTheChromiumReference() {
        // gradient-longer-hue-hsl-013 row 1 — the powerless-carry wheel.
        let red = GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 0)
        let black = GradientApplier.RGBAStop(r: 0, g: 0, b: 0, a: 1, loc: 1)
        // The box is only 100px wide, so the wheel turns 3.6°/px and the
        // red channel moves ~15/255 per pixel through the 60°–120° band
        // (t = 0.25 sits at 90°): one pixel of probe alignment is the
        // honest tolerance here, not the 4/255 the 200px boxes allow.
        for (t, px) in [(0.25, (91, 166, 25)), (0.5, (31, 92, 94)), (0.75, (32, 24, 39))] {
            let s = GradientRamp.interpolate(red, black, t: t, interp: GradientInterpolation(space: .hsl, hue: .longer))
            assertRef(s, px, "longer t=\(t)", tolerance: 16)
        }
    }

    // MARK: - Colour-space round trips

    func testEverySpaceRoundTripsSrgb() {
        let spaces: [GradientInterpolation.Space] = [.srgb, .srgbLinear, .lab, .oklab, .xyzD65, .xyzD50, .hsl, .hwb, .lch, .oklch]
        let samples: [GradientColorMath.Triple] = [(1, 0, 0), (0, 0.5019608, 0), (0.2, 0.4, 0.9), (1, 1, 1), (0, 0, 0), (0.5, 0.5, 0.5)]
        for space in spaces {
            for c in samples {
                let back = GradientColorMath.toSrgb(GradientColorMath.fromSrgb(c, to: space), from: space)
                XCTAssertEqual(back.0, c.0, accuracy: 1e-6, "\(space) r")
                XCTAssertEqual(back.1, c.1, accuracy: 1e-6, "\(space) g")
                XCTAssertEqual(back.2, c.2, accuracy: 1e-6, "\(space) b")
            }
        }
    }

    func testKnownOklchAndLabCoordinates() {
        // css-color-4 worked examples: sRGB red ≈ oklch(0.628 0.258 29.23°),
        // sRGB white = lab(100 0 0).
        let red = GradientColorMath.fromSrgb((1, 0, 0), to: .oklch)
        XCTAssertEqual(red.0, 0.628, accuracy: 0.002)
        XCTAssertEqual(red.1, 0.258, accuracy: 0.002)
        XCTAssertEqual(red.2, 29.23, accuracy: 0.1)
        let white = GradientColorMath.fromSrgb((1, 1, 1), to: .lab)
        XCTAssertEqual(white.0, 100, accuracy: 0.01)
        XCTAssertEqual(white.1, 0, accuracy: 0.01)
        XCTAssertEqual(white.2, 0, accuracy: 0.01)
        // Powerless analysis: white/black/gray hues are powerless in every
        // polar space; red's is not.
        for space: GradientInterpolation.Space in [.hsl, .hwb, .lch, .oklch] {
            XCTAssertTrue(GradientColorMath.hueIsPowerless(GradientColorMath.fromSrgb((1, 1, 1), to: space), in: space), "\(space)")
            XCTAssertTrue(GradientColorMath.hueIsPowerless(GradientColorMath.fromSrgb((0, 0, 0), to: space), in: space), "\(space)")
            XCTAssertFalse(GradientColorMath.hueIsPowerless(GradientColorMath.fromSrgb((1, 0, 0), to: space), in: space), "\(space)")
        }
    }
}
