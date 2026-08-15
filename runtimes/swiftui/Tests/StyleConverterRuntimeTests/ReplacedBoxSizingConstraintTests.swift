//
//  ReplacedBoxSizingConstraintTests.swift
//  Wave-42 lane W6 — unit pins for CSS 2.1 §10.4 constraint-violation sizing
//  of replaced elements (ReplacedBoxSizing.constrainAutoSize and its
//  wire-facing caller ReplacedImageContent.constrainedAutoContentSize).
//
//  Line-for-line twin of the Kotlin ReplacedBoxSizingConstraintTest: the
//  corpus rows use the EXACT geometry the css-ui box-sizing-007..025 family
//  delivers once the wave-40 SVG pre-raster hop stands in for the vectors
//  (tools/titan/svg-preraster.mjs's probed natural sizes):
//
//    w100_h100 / w100_r1-1 / h100_r1-1 → 100×100   r1-1 → 150×150
//    w100 → 100×150   h100 → 300×100
//
//  and the CONTENT-BOX bounds each test's declared border-box min/max resolves
//  to after its 30px padding band (`max-height: 100px` + `padding-bottom:
//  30px` under `box-sizing: border-box` → content max-height 70). The two
//  natives must agree on this table to the pixel.
//

import XCTest
@testable import StyleConverterRuntime

final class ReplacedBoxSizingConstraintTests: XCTestCase {

    /// Shorthand: run the table and assert both axes to 1e-3 px.
    private func assertUsed(
        _ expectedW: Double, _ expectedH: Double,
        w: Double, h: Double, ratio: Double?,
        minW: Double? = nil, maxW: Double? = nil,
        minH: Double? = nil, maxH: Double? = nil,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        let used = ReplacedBoxSizing.constrainAutoSize(
            intrinsicWidthPx: w, intrinsicHeightPx: h, aspectRatio: ratio,
            minWidthPx: minW, maxWidthPx: maxW, minHeightPx: minH, maxHeightPx: maxH)
        XCTAssertEqual(used.widthPx, expectedW, accuracy: 1e-3, "width", file: file, line: line)
        XCTAssertEqual(used.heightPx, expectedH, accuracy: 1e-3, "height", file: file, line: line)
    }

    // ── Row 1: no violation — the identity every pre-wave-42 caller needs ──

    /// The whole dark-stage corpus (no min/max on any replaced element) must
    /// render byte-identical through the new path.
    func testNoDeclaredBoundsIsTheIdentity() {
        assertUsed(100, 100, w: 100, h: 100, ratio: 1)
        assertUsed(300, 100, w: 300, h: 100, ratio: 3)
    }

    /// box-sizing-018's h100_r1-1 under max-height 100: h == maxH is NOT a
    /// violation (§10.4 uses strict inequalities), so only maxW 70 fires and
    /// the single-violation row (not row 6/7) resolves to 70×70.
    func testBoundMetExactlyIsNotAViolation() {
        assertUsed(70, 70, w: 100, h: 100, ratio: 1, maxW: 70, maxH: 100)
    }

    // ── Rows 2/3: max on one axis re-solves the other via the ratio ────────

    func testMaxHeightViolationResolvesWidthThroughRatio() {
        // box-sizing-010/014 (100×100, content maxH 70) → the ref's 70×70.
        assertUsed(70, 70, w: 100, h: 100, ratio: 1, maxH: 70)
        // box-sizing-016 (r1-1 pre-rastered at 150×150) — the wave-41 T2
        // blocker: this used to paint 150 wide and wrap; §10.4 gives 70×70.
        assertUsed(70, 70, w: 150, h: 150, ratio: 1, maxH: 70)
        // box-sizing-019 (maxW 100 satisfied, maxH 70 violated) → 70×70.
        assertUsed(70, 70, w: 100, h: 100, ratio: 1, maxW: 100, maxH: 70)
        // box-sizing-012 (w100 pre-rastered at 100×150): the raster ASSERTS a
        // 2:3 ratio the real vector never had (svg-preraster.mjs limitation
        // 2), so the honest §10.4 answer is 46.667×70 — NOT the browser ref's
        // 100×70. Pinned so the divergence is a documented number, not drift.
        assertUsed(70.0 * 100.0 / 150.0, 70, w: 100, h: 150, ratio: 100.0 / 150.0, maxH: 70)
    }

    func testMaxWidthViolationResolvesHeightThroughRatio() {
        // box-sizing-011/015/017 (content maxW 70) → 70×70 on every 1:1 raster.
        assertUsed(70, 70, w: 100, h: 100, ratio: 1, maxW: 70)
        assertUsed(70, 70, w: 150, h: 150, ratio: 1, maxW: 70)
        // box-sizing-013 (h100 pre-rastered at 300×100, ratio 3): 70×23.333 —
        // again the raster's asserted ratio, not the vector's absent one.
        assertUsed(70, 70.0 * 100.0 / 300.0, w: 300, h: 100, ratio: 3, maxW: 70)
    }

    /// Row 2's max(…, min-height) arm: 200×100 capped to maxW 100 would
    /// derive h 50, but minH 80 floors it — the spec's cross-clamp.
    func testResolvedAxisIsFlooredAtItsOwnMin() {
        assertUsed(100, 80, w: 200, h: 100, ratio: 2, maxW: 100, minH: 80)
    }

    // ── Rows 4/5: min on one axis re-solves the other via the ratio ────────

    func testMinHeightViolationResolvesWidthThroughRatio() {
        // box-sizing-020/024 (100×100, content minH 130) → the ref's 130×130.
        assertUsed(130, 130, w: 100, h: 100, ratio: 1, minH: 130)
        // box-sizing-023 (h100 pre-rastered at 300×100): 390×130 — the honest
        // §10.4 answer for the raster's asserted 3:1 ratio (ref: 300×130).
        assertUsed(130.0 * 300.0 / 100.0, 130, w: 300, h: 100, ratio: 3, minH: 130)
    }

    func testMinWidthViolationResolvesHeightThroughRatio() {
        // box-sizing-021/025 (100×100, content minW 130) → the ref's 130×130.
        assertUsed(130, 130, w: 100, h: 100, ratio: 1, minW: 130)
        // box-sizing-022 (w100 pre-rastered at 100×150): 130×195 (ref 130×150).
        assertUsed(130, 130.0 * 150.0 / 100.0, w: 100, h: 150, ratio: 100.0 / 150.0, minW: 130)
    }

    /// Row 4's min(…, max-height) arm: 100×200 grown to minW 200 would derive
    /// h 400, but maxH 300 caps it.
    func testResolvedAxisIsCappedAtItsOwnMax() {
        assertUsed(200, 300, w: 100, h: 200, ratio: 0.5, minW: 200, maxH: 300)
    }

    // ── Rows 6/7: both too large — the MOST violated axis drives ───────────

    func testBothMaxesViolatedShrinkByTheSmallerScaleFactor() {
        // maxW/w = 0.25 ≤ maxH/h = 0.9 → width drives: (50, 25).
        assertUsed(50, 25, w: 200, h: 100, ratio: 2, maxW: 50, maxH: 90)
        // maxW/w = 0.95 > maxH/h = 0.25 → height drives: (50, 25).
        assertUsed(50, 25, w: 200, h: 100, ratio: 2, maxW: 190, maxH: 25)
    }

    // ── Rows 8/9: both too small — the MOST violated axis drives ───────────

    func testBothMinsViolatedGrowByTheLargerScaleFactor() {
        // minW/w = 1.1 ≤ minH/h = 1.4 → height drives: (min(∞, 280), 140).
        assertUsed(280, 140, w: 200, h: 100, ratio: 2, minW: 220, minH: 140)
        // minW/w = 1.5 > minH/h = 1.1 → width drives: (300, min(∞, 150)).
        assertUsed(300, 150, w: 200, h: 100, ratio: 2, minW: 300, minH: 110)
    }

    /// The wave-42 skeptic vector: both mins violated AND the derived axis
    /// over-shoots a declared max. 50×50 raster, minW 100 / minH 70 / maxH 70
    /// → minW/w = 2.0 > minH/h = 1.4, so row 9 fires and derives
    /// h = 100·50/50 = 100 — which max-height 70 must CAP. §10.4 row 9:
    /// "min-width, min(max-height, min-width·h/w)" → 100×70. The pre-fix
    /// `max(min-height, …)` transcription returned 100×100, i.e. 30px of
    /// declared max-height silently ignored.
    func testRow9DerivedHeightIsCappedByMaxHeightNotFlooredAtMin() {
        assertUsed(100, 70, w: 50, h: 50, ratio: 1, minW: 100, minH: 70, maxH: 70)
    }

    // ── Row 10: opposite squeezes abandon the ratio ────────────────────────

    func testOppositeDirectionViolationsTakeBothBounds() {
        // (w < minW) and (h > maxH) → (minW, maxH) verbatim.
        assertUsed(130, 70, w: 100, h: 100, ratio: 1, minW: 130, maxH: 70)
        // (w > maxW) and (h < minH) → (maxW, minH) verbatim.
        assertUsed(70, 130, w: 100, h: 100, ratio: 1, maxW: 70, minH: 130)
    }

    // ── §10.4 preamble: max(min, max), and the no-ratio fallback ───────────

    /// §10.4: "use max(min, max)": maxW 50 under minW 80 becomes 80.
    func testMaxBelowItsMinIsRaisedToTheMin() {
        assertUsed(80, 80, w: 100, h: 100, ratio: 1, minW: 80, maxW: 50)
    }

    /// No ratio ⇒ nothing links the axes: minW grows width, maxH shrinks
    /// height, neither propagates (§10.4's non-ratio algorithm).
    func testDegenerateRatioClampsEachAxisIndependently() {
        assertUsed(120, 70, w: 100, h: 100, ratio: nil, minW: 120, maxH: 70)
        assertUsed(120, 70, w: 100, h: 100, ratio: 0, minW: 120, maxH: 70)
    }

    // ── the wire half: border-box band subtraction in the caller ───────────

    /// box-sizing-010's exact style: border-box, padding-bottom 30, max-height
    /// 100 → content maxH 70 → the ref's 70×70 square.
    func testExplicitBorderBoxSubtractsThePaddingBand() {
        var s = ComponentStyle()
        s.size.boxSizing = .borderBox
        s.size.maxHeight = .exact(px: 100)
        s.spacing.padding = PaddingConfig(bottom: .exact(px: 30))
        let used = ReplacedImageContent.constrainedAutoContentSize(
            style: s, intrinsicWidthPx: 100, intrinsicHeightPx: 100, aspectRatio: 1)
        XCTAssertEqual(used.widthPx, 70, accuracy: 1e-3)
        XCTAssertEqual(used.heightPx, 70, accuracy: 1e-3)
    }

    /// box-sizing-021's exact style: border-box, padding-RIGHT 30, min-width
    /// 160 → content minW 130 → the ref's 130×130 square. The vertical axis
    /// keeps its (absent) bounds — no cross-axis leakage.
    func testWidthAxisBandComesFromHorizontalPaddingOnly() {
        var s = ComponentStyle()
        s.size.boxSizing = .borderBox
        s.size.minWidth = .exact(px: 160)
        s.spacing.padding = PaddingConfig(right: .exact(px: 30))
        let used = ReplacedImageContent.constrainedAutoContentSize(
            style: s, intrinsicWidthPx: 100, intrinsicHeightPx: 100, aspectRatio: 1)
        XCTAssertEqual(used.widthPx, 130, accuracy: 1e-3)
        XCTAssertEqual(used.heightPx, 130, accuracy: 1e-3)
    }

    /// Unset box-sizing = the chain's border-box status quo (SizeConfig's
    /// tri-state: nil is NOT content-box), so the band still subtracts. The
    /// WPT unset-defaults-to-content-box decision is folded onto the config
    /// by the renderer BEFORE this helper runs (effectiveBoxSizing), so this
    /// helper sees only the effective keyword.
    func testUnsetBoxSizingKeepsTheBorderBoxStatusQuo() {
        var s = ComponentStyle()
        s.size.maxHeight = .exact(px: 100)
        s.spacing.padding = PaddingConfig(bottom: .exact(px: 30))
        let used = ReplacedImageContent.constrainedAutoContentSize(
            style: s, intrinsicWidthPx: 100, intrinsicHeightPx: 100, aspectRatio: 1)
        XCTAssertEqual(used.widthPx, 70, accuracy: 1e-3)
        XCTAssertEqual(used.heightPx, 70, accuracy: 1e-3)
    }

    /// Effective content-box: the declared bound IS the content bound, so the
    /// declared maxH 100 is not violated by 100×100 — identity.
    func testContentBoxKeepsTheFullBound() {
        var s = ComponentStyle()
        s.size.boxSizing = .contentBox
        s.size.maxHeight = .exact(px: 100)
        s.spacing.padding = PaddingConfig(bottom: .exact(px: 30))
        let used = ReplacedImageContent.constrainedAutoContentSize(
            style: s, intrinsicWidthPx: 100, intrinsicHeightPx: 100, aspectRatio: 1)
        XCTAssertEqual(used.widthPx, 100, accuracy: 1e-3)
        XCTAssertEqual(used.heightPx, 100, accuracy: 1e-3)
    }

    /// `max-height: none` arrives as `.none` → not an `.exact` px → no bound.
    /// Collapsing it to 0 would shrink every unconstrained box.
    func testExplicitNoneBoundIsUnboundedNotZero() {
        var s = ComponentStyle()
        s.size.maxHeight = LengthValue.none
        let used = ReplacedImageContent.constrainedAutoContentSize(
            style: s, intrinsicWidthPx: 100, intrinsicHeightPx: 100, aspectRatio: 1)
        XCTAssertEqual(used.widthPx, 100, accuracy: 1e-3)
        XCTAssertEqual(used.heightPx, 100, accuracy: 1e-3)
    }

    /// css-ui-3 §5 (the BorderBoxFloor rule): max-height 20 inside a 30px
    /// padding band leaves a 0 content bound — the content vanishes, the
    /// padding does not. Width follows through the ratio to 0 as well.
    func testBoundSmallerThanItsBandFloorsAtZero() {
        var s = ComponentStyle()
        s.size.boxSizing = .borderBox
        s.size.maxHeight = .exact(px: 20)
        s.spacing.padding = PaddingConfig(bottom: .exact(px: 30))
        let used = ReplacedImageContent.constrainedAutoContentSize(
            style: s, intrinsicWidthPx: 100, intrinsicHeightPx: 100, aspectRatio: 1)
        XCTAssertEqual(used.widthPx, 0, accuracy: 1e-3)
        XCTAssertEqual(used.heightPx, 0, accuracy: 1e-3)
    }
}
