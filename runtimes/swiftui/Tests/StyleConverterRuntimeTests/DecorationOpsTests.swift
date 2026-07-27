//
//  DecorationOpsTests.swift
//  StyleConverterRuntimeTests
//
//  DecorationOps pin table — applier campaign wave 21, lane TEXTDECOR
//  (B-RC8 decoration style/thickness, B-RC7 unbreakable runs).
//
//  TWIN of the Compose suite DecorationOpsTest.kt — SAME cases, SAME
//  expected numbers (repo twin rule: identical pin tables). Change one,
//  change both.
//
//  Every number below is pinned against LIVE wave-21 artifacts:
//    • ref PNGs: tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//      white-black-ink-font-lh/css-text-decor/text-decoration-dotted-001
//      .png and -002.png (390×600). Measured red underline bands
//      (thickness 10/20/30 @92px Arial, dots start at x=62):
//        -001 (run width solved W=460): pitches 19.565 / 40.000 / 61.429,
//          band top rows 207 / 363 / 519, text ink bottoms 202 / 353 / 504
//        -002 (W=409): pitches 19.950 / 38.900 / 63.167
//    • per-test IR: tools/titan/runs/wave21-gate/sections/css-text-decor/
//      per-test-ir/wpt__css-text-decor__text-decoration-dotted-00{1,2}
//      .json — TextDecorationStyle "DOTTED", TextDecorationThickness
//      {"type":"length","px":10|20|30}, text "fooשלוםbaz" / "foobarbaz".
//    • Blink algorithm: styled_stroke_data.cc (SelectBestDashGap,
//      DashEffectFromStrokeStyle) + decoration_line_painter.cc
//      (DrawLineAsStroke) — cited per-function in DecorationOps.swift.
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class DecorationOpsTests: XCTestCase {

    // MARK: - helpers

    /// Dots of a dotted op list (fails loudly on stray bands).
    private func dots(_ ops: [DecorationOps.Op]) -> [(cx: CGFloat, cy: CGFloat, r: CGFloat)] {
        ops.map { op in
            guard case let .dot(cx, cy, r) = op else {
                XCTFail("expected only dots, got \(op)"); return (0, 0, 0)
            }
            return (cx, cy, r)
        }
    }

    /// Bands of a dashed/solid op list (fails loudly on stray dots).
    private func bands(_ ops: [DecorationOps.Op]) -> [(l: CGFloat, t: CGFloat, w: CGFloat, h: CGFloat)] {
        ops.map { op in
            guard case let .band(l, t, w, h) = op else {
                XCTFail("expected only bands, got \(op)"); return (0, 0, 0, 0)
            }
            return (l, t, w, h)
        }
    }

    // MARK: - SelectBestDashGap: the Chromium dot-rhythm fit

    func testDashGapFitReproducesDotted001MeasuredPitchesAtW460() {
        // t=10: ref pitch 19.565 → gap 9.565 (max-count branch wins).
        XCTAssertEqual(DecorationOps.selectBestDashGap(strokeLength: 460, dashLength: 10, gapLength: 10),
                       9.5652174, accuracy: 1e-3)
        // t=20: ref pitch 40.000 exactly → gap 20 (perfect min fit).
        XCTAssertEqual(DecorationOps.selectBestDashGap(strokeLength: 460, dashLength: 20, gapLength: 20),
                       20.0, accuracy: 1e-3)
        // t=30: ref pitch 61.429 → gap 31.429 (min-count branch wins).
        XCTAssertEqual(DecorationOps.selectBestDashGap(strokeLength: 460, dashLength: 30, gapLength: 30),
                       31.428572, accuracy: 1e-3)
    }

    func testDashGapFitReproducesDotted002MeasuredPitchesAtW409() {
        // Same algorithm, second measured run width — all three rows.
        XCTAssertEqual(DecorationOps.selectBestDashGap(strokeLength: 409, dashLength: 10, gapLength: 10),
                       9.95, accuracy: 1e-3)
        XCTAssertEqual(DecorationOps.selectBestDashGap(strokeLength: 409, dashLength: 20, gapLength: 20),
                       18.9, accuracy: 1e-3)
        XCTAssertEqual(DecorationOps.selectBestDashGap(strokeLength: 409, dashLength: 30, gapLength: 30),
                       33.166668, accuracy: 1e-3)
    }

    // MARK: - dotted: thick (>3px) circle runs

    func testDottedT20OverTheDotted001RunEmits12FlushDots() {
        let d = dots(DecorationOps.dottedOps(left: 0, top: 0, width: 460, thicknessPx: 20))
        // Blink: pitch 40−ε over the inset 440px path → 12 dots.
        XCTAssertEqual(d.count, 12)
        // First dot: center = inset start (t/2), radius t/2, centerY =
        // floor(top + t/2) = 10. Left EDGE = 0 — ref shows dot edges
        // flush with the run start (x=62 in canvas space) for every t.
        XCTAssertEqual(d[0].cx, 10, accuracy: 1e-3)
        XCTAssertEqual(d[0].cy, 10, accuracy: 1e-3)
        XCTAssertEqual(d[0].r, 10, accuracy: 1e-3)
        // Last dot rides the ε-guarantee to the run end: 10 + 11×39.99.
        XCTAssertEqual(d[11].cx, 449.89, accuracy: 1e-2)
        // Pitch between consecutive centers = gap + t − ε = 39.99.
        XCTAssertEqual(d[1].cx - d[0].cx, 39.99, accuracy: 1e-3)
    }

    func testDottedT10AndT30DotCountsMatchTheFittedRhythm() {
        // t=10: pitch 19.555 over 450px inset path → 24 dots.
        XCTAssertEqual(dots(DecorationOps.dottedOps(left: 0, top: 0, width: 460, thicknessPx: 10)).count, 24)
        // t=30: pitch 61.419 over 430px inset path → 8 dots.
        let d30 = dots(DecorationOps.dottedOps(left: 0, top: 0, width: 460, thicknessPx: 30))
        XCTAssertEqual(d30.count, 8)
        // Second dot's left edge = center − 15 ≈ 61.42; canvas-space
        // 62 + 61.42 = 123.42 → ref band -001 t=30 second dot columns
        // 124..152 (AA edges) — the measured pin behind the pitch.
        XCTAssertEqual(d30[1].cx - d30[0].cx, 61.419, accuracy: 1e-2)
    }

    func testDottedBandVerticalGeometryMatchesTheRefRows() {
        // Feed the REAL ref band tops (rows 207/363/519) and pin the
        // dot centers to the measured band midlines: a t-thick band
        // starting at top spans [top, top+t) → center floor(top + t/2)
        // = 212 / 373 / 534, exactly the ref circles' center rows.
        XCTAssertEqual(dots(DecorationOps.dottedOps(left: 62, top: 207, width: 460, thicknessPx: 10))[0].cy, 212)
        XCTAssertEqual(dots(DecorationOps.dottedOps(left: 62, top: 363, width: 460, thicknessPx: 20))[0].cy, 373)
        XCTAssertEqual(dots(DecorationOps.dottedOps(left: 62, top: 519, width: 460, thicknessPx: 30))[0].cy, 534)
    }

    func testDottedNarrowerThanTwoDotsPaintsASingleDot() {
        // width < 2t (Blink "just draw 1"): one circle at the inset start.
        let d = dots(DecorationOps.dottedOps(left: 0, top: 0, width: 30, thicknessPx: 20))
        XCTAssertEqual(d.count, 1)
        XCTAssertEqual(d[0].cx, 10, accuracy: 1e-3)
    }

    func testDottedAtExactlyTwoDotWidthPaintsOneDotNotZero() {
        // width == 2t: Blink's `<` keeps the fit path, SelectBestDashGap
        // divides by ZERO gaps → +Inf gap — but Skia still paints the
        // dash interval's phase-0 dot, so ONE dot renders (wave-21
        // skeptic pin: the pre-fix loop's 0·Inf = NaN painted NOTHING).
        let d = dots(DecorationOps.dottedOps(left: 0, top: 0, width: 20, thicknessPx: 10))
        XCTAssertEqual(d.count, 1)
        // Same inset-start center as the narrow single-dot branch.
        XCTAssertEqual(d[0].cx, 5, accuracy: 1e-3)
    }

    func testThinDotted3pxAndUnderPaintsSquareDashesNotCircles() {
        // Blink StrokeIsDashed: t ≤ 3 → butt-cap t-on/t-off dashes with
        // NO gap refit. t=2, W=20 → dashes at x = 0,4,8,12,16.
        let ops = bands(DecorationOps.dottedOps(left: 0, top: 0, width: 20, thicknessPx: 2))
        XCTAssertEqual(ops.count, 5)
        // midY = floor(0 + 1) = 1 (even t, no half shift) → top 0.
        XCTAssertEqual(ops[0].t, 0)
        XCTAssertEqual(ops[0].w, 2)
        XCTAssertEqual(ops[0].h, 2)
        XCTAssertEqual(ops[1].l, 4)
    }

    // MARK: - dashed: Blink's three branches

    func testDashedTooShortForTwoDashesPaintsSolid() {
        // t=10 → dash 20; W=35 ≤ 2×20 → Blink nullopt → solid stroke.
        let ops = bands(DecorationOps.dashedOps(left: 0, top: 0, width: 35, thicknessPx: 10))
        XCTAssertEqual(ops.count, 1)
        // Stroke centered on midY=5 → band rows [0,10).
        XCTAssertEqual(ops[0].t, 0)
        XCTAssertEqual(ops[0].w, 35)
        XCTAssertEqual(ops[0].h, 10)
    }

    func testDashedTwoDashWindowScalesDashAndGapProportionally() {
        // t=10 → dash 20, gap 20; 40 < W=50 ≤ 60 → mult 50/60: two
        // dashes of 16.667 with the second flush at the end.
        let ops = bands(DecorationOps.dashedOps(left: 0, top: 0, width: 50, thicknessPx: 10))
        XCTAssertEqual(ops.count, 2)
        XCTAssertEqual(ops[0].w, 16.666666, accuracy: 1e-3)
        // Second dash starts at (dash+gap)·m = 33.333, ends at 50.
        XCTAssertEqual(ops[1].l, 33.333332, accuracy: 1e-3)
        XCTAssertEqual(ops[1].l + ops[1].w, 50, accuracy: 1e-3)
    }

    func testDashedLongRunRefitsTheGapAndStaysFlush() {
        // t=10 → dash 20, ideal gap 20 over W=460: refit keeps gap 20
        // (perfect fit) → dashes at 0,40,…,440 — twelve of width 20.
        let ops = bands(DecorationOps.dashedOps(left: 0, top: 0, width: 460, thicknessPx: 10))
        XCTAssertEqual(ops.count, 12)
        XCTAssertEqual(ops[11].l, 440, accuracy: 1e-3)
        XCTAssertEqual(ops[11].w, 20, accuracy: 1e-3)
        // Flush pin: at W=450 the REFIT gap (19.09) spreads 12 dashes so
        // the last one ends exactly at the run end — SelectBestDashGap
        // always lands the final dash flush (the min() clip in the
        // emitter only fires for the thin-dotted FIXED-gap pattern).
        let refit = bands(DecorationOps.dashedOps(left: 0, top: 0, width: 450, thicknessPx: 10))
        XCTAssertEqual(refit[refit.count - 1].l + refit[refit.count - 1].w, 450, accuracy: 1e-2)
    }

    // MARK: - solid identity + honest double/wavy fallback

    func testSolidEmitsTheBandItselfByteForByte() {
        // The legacy Rectangle frame — auto-path captures cannot move.
        let ops = DecorationOps.styleOps(left: 20, top: 43, width: 212, thicknessPx: 2, style: .solid)
        XCTAssertEqual(ops, [.band(left: 20, top: 43, width: 212, height: 2)])
    }

    func testDoubleAndWavyFallBackToSolidAndAreFlaggedLossy() {
        // Fallback is the same solid band…
        XCTAssertEqual(
            DecorationOps.styleOps(left: 0, top: 0, width: 100, thicknessPx: 2, style: .double),
            [.band(left: 0, top: 0, width: 100, height: 2)]
        )
        // …and the caller-visible lossy flag names exactly those two.
        XCTAssertTrue(DecorationOps.isLossyFallback(.double))
        XCTAssertTrue(DecorationOps.isLossyFallback(.wavy))
        XCTAssertFalse(DecorationOps.isLossyFallback(.solid))
        XCTAssertFalse(DecorationOps.isLossyFallback(.dotted))
        XCTAssertFalse(DecorationOps.isLossyFallback(.dashed))
    }

    // MARK: - explicit-thickness placement (css-text-decor-4 §2.4)

    func testExplicitUnderlineGapIsBlinksCeilHalfRule() {
        // Ref-pinned: band top − ink bottom = 5/10/15 for T=10/20/30.
        XCTAssertEqual(DecorationOps.explicitUnderlineGapPx(thicknessPx: 10), 5)
        XCTAssertEqual(DecorationOps.explicitUnderlineGapPx(thicknessPx: 20), 10)
        XCTAssertEqual(DecorationOps.explicitUnderlineGapPx(thicknessPx: 30), 15)
        // Thin floors: max(1, ceil(T/2)).
        XCTAssertEqual(DecorationOps.explicitUnderlineGapPx(thicknessPx: 1), 1)
        XCTAssertEqual(DecorationOps.explicitUnderlineGapPx(thicknessPx: 2), 1)
        XCTAssertEqual(DecorationOps.explicitUnderlineGapPx(thicknessPx: 3), 2)
    }

    func testExplicitUnderlineTopAnchorsOnTheSnappedAscent() {
        // iOS anchors per-line offsets on the LINE-BOX TOP; the snapped
        // baseline is the rounded ascent. With the ref-derived Arial
        // ascents the same gap rule reproduces the Compose twin's tops:
        // ascent 92·0.905 = 83.3 → round 83; T=10 → 83 + 5 = 88 from
        // the line top (Compose pins the identical band via baseline
        // 202 → top 207: 202 − 83 = 119 line top; 119 + 88 = 207 ✓).
        XCTAssertEqual(DecorationOps.explicitUnderlineTop(ascentPx: 83.26, thicknessPx: 10), 88)
        // The wave-5 oracle face (22px Inter, ascent 21.3125): explicit
        // T=2 → round(21.3125) + max(1, ceil(1)) = 21 + 1 = 22 —
        // INTENTIONALLY 1px above the auto rule's 22 + gap==T… i.e. the
        // auto row is underlineTop = round(21.3125 + 2) = 23; explicit
        // T=2 gives 22. Documented seam: auto keeps the capture-pinned
        // rows (DecorationMetrics), explicit uses Blink's ceil-half gap
        // — same divergence the Compose twin pins at T=2.
        XCTAssertEqual(DecorationOps.explicitUnderlineTop(ascentPx: 21.3125, thicknessPx: 2), 22)
    }

    func testExplicitStrikeStraddlesTheAutoCenterAndOverlineHangsAbove() {
        // Strike (22px Inter, ascent 21.3125): auto top = round(21.3125
        // − 8) = 13, auto thickness 2 → center 14. Explicit T=10 →
        // round(14 − 5) = 9.
        XCTAssertEqual(
            DecorationOps.explicitLineThroughTop(ascentPx: 21.3125, fontSizePx: 22, thicknessPx: 10), 9)
        // Overline: bottom flush with the line top → top = −T.
        XCTAssertEqual(DecorationOps.explicitOverlineTop(thicknessPx: 10), -10)
    }

    // MARK: - B-RC8 wire pin: the LIVE thickness envelope must survive

    /// Decode an [IRProperty] from inline JSON — the production decode
    /// path (IOSDecorationOwnershipTests helper pattern).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    func testLiveLengthWireReachesTheThicknessConfig() {
        // The EXACT wave-21 wire (per-test IR, text-decoration-dotted-001
        // components 1-3): {"type":"length","px":10|20|30}. The pre-fix
        // extractor treated the "type" discriminator as a KEYWORD (via
        // extractKeyword's o["type"] fallback) and nil'd the px out —
        // every explicit thickness silently dropped on iOS.
        let cfg = TextDecorationThicknessExtractor.extract(from: props(
            #"[{"type":"TextDecorationThickness","data":{"type":"length","px":10}}]"#))
        XCTAssertEqual(cfg?.px, 10)
        // `auto` (the css-text-decor-4 §2.4 initial) still resolves to
        // nil px — the platform-default branch is value-matched now.
        let auto = TextDecorationThicknessExtractor.extract(from: props(
            #"[{"type":"TextDecorationThickness","data":"auto"}]"#))
        XCTAssertNotNil(auto)
        XCTAssertNil(auto?.px)
    }

    // MARK: - B-RC7: unbreakable-run detection (UAX #14 approximation)

    func testTheDottedTestStringsAreUnbreakable() {
        // The LIVE IR texts: no spaces, no break punctuation, Hebrew is
        // not ideographic → the ref keeps each on ONE overflowing line.
        XCTAssertFalse(DecorationOps.hasSoftWrapOpportunity("fooשלוםbaz"))
        XCTAssertFalse(DecorationOps.hasSoftWrapOpportunity("foobarbaz"))
        // Empty string: nothing to wrap.
        XCTAssertFalse(DecorationOps.hasSoftWrapOpportunity(""))
        // NBSP FORBIDS breaks (css-text-3 §5.2 / UAX #14 GL).
        XCTAssertFalse(DecorationOps.hasSoftWrapOpportunity("foo\u{00A0}bar"))
    }

    func testSpacesHyphensSoftHyphensAndIdeographsAreWrapOpportunities() {
        // Plain space (UAX #14 SP).
        XCTAssertTrue(DecorationOps.hasSoftWrapOpportunity("foo bar"))
        // Break-after hyphen (class HY).
        XCTAssertTrue(DecorationOps.hasSoftWrapOpportunity("foo-bar"))
        // Soft hyphen + zero-width space (explicit opportunities).
        XCTAssertTrue(DecorationOps.hasSoftWrapOpportunity("foo\u{00AD}bar"))
        XCTAssertTrue(DecorationOps.hasSoftWrapOpportunity("foo\u{200B}bar"))
        // Ideographic run (class ID — breaks between any two).
        XCTAssertTrue(DecorationOps.hasSoftWrapOpportunity("日本語"))
        // Em dash (class BA).
        XCTAssertTrue(DecorationOps.hasSoftWrapOpportunity("foo\u{2014}bar"))
        // NEL U+0085 (class BK, mandatory break): Swift's White_Space
        // includes it natively; the Kotlin twin carves it IN because
        // Java's whitespace sets exclude it (skeptic parity pin).
        XCTAssertTrue(DecorationOps.hasSoftWrapOpportunity("foo\u{0085}bar"))
    }
}
