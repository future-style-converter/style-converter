//
//  BackgroundTileMathTests.swift
//  StyleConverterRuntimeTests
//
//  Pins css-backgrounds-3 §3.7 tile placement (BackgroundTileMath) — the
//  iOS port of the Compose runtime's BackgroundTileMath.kt, mirrored
//  test-for-test against BackgroundTileMathTest.kt so both native
//  platforms are provably running the same space/round arithmetic.
//  Also pins the gradient-specific size resolution (§3.9 for
//  intrinsic-less images) and the applier's geometry-routing predicate —
//  pure math only, no rendering.
//

import Foundation
import CoreGraphics
import XCTest
@testable import StyleConverterRuntime

final class BackgroundTileMathTests: XCTestCase {

    /// Origin-list comparison with a float tolerance (mirrors the Kotlin
    /// helper's 0.01f epsilon).
    private func assertOrigins(_ expected: [CGFloat], _ plan: BackgroundTileMath.AxisPlan,
                               file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(expected.count, plan.origins.count, "origin count", file: file, line: line)
        for (i, pair) in zip(expected, plan.origins).enumerated() {
            XCTAssertEqual(pair.0, pair.1, accuracy: 0.01, "origin \(i)", file: file, line: line)
        }
    }

    // MARK: - space (§3.7 whole tiles + equal gaps)

    func testSpaceDistributesLeftoverAsEqualGapsWithFlushEdges() {
        // 140px area, 50px tile → 2 whole tiles, 40px leftover → one 40px
        // gap; first at 0, last flush at 140−50=90.
        let plan = BackgroundTileMath.axisPlan(area: 140, tile: 50, anchor: 0, mode: .space)
        XCTAssertEqual(plan.tileSize, 50, accuracy: 0.001) // space never rescales
        assertOrigins([0, 90], plan)
    }

    func testSpaceWithThreeTilesSplitsLeftoverIntoTwoGaps() {
        // 170 / 50 → 3 tiles, leftover 20 → 10px gaps: 0, 60, 120 (flush).
        assertOrigins([0, 60, 120],
                      BackgroundTileMath.axisPlan(area: 170, tile: 50, anchor: 0, mode: .space))
    }

    func testSpaceWithRoomForOneTileFallsBackToThePositionAnchor() {
        // §3.7: fewer than two fitting → background-position decides.
        assertOrigins([12],
                      BackgroundTileMath.axisPlan(area: 80, tile: 50, anchor: 12, mode: .space))
    }

    // MARK: - round (§3.7 integer-count rescale)

    func testRoundRescalesTheTileSoAWholeCountFits() {
        // 140 / 50 = 2.8 → rounds to 3 tiles of 140/3 ≈ 46.67.
        let plan = BackgroundTileMath.axisPlan(area: 140, tile: 50, anchor: 0, mode: .round)
        XCTAssertEqual(plan.tileSize, 140.0 / 3.0, accuracy: 0.001)
        assertOrigins([0, 140.0 / 3.0, 280.0 / 3.0], plan)
    }

    func testRoundKeepsAnExactFitUntouched() {
        let plan = BackgroundTileMath.axisPlan(area: 100, tile: 50, anchor: 0, mode: .round)
        XCTAssertEqual(plan.tileSize, 50, accuracy: 0.001)
        assertOrigins([0, 50], plan)
    }

    func testRoundGrowsAnOversizedTileUpToTheFullArea() {
        // 100 / 80 = 1.25 → rounds to 1 tile stretched to the whole axis
        // (round never drops below one tile).
        let plan = BackgroundTileMath.axisPlan(area: 100, tile: 80, anchor: 0, mode: .round)
        XCTAssertEqual(plan.tileSize, 100, accuracy: 0.001)
        assertOrigins([0], plan)
    }

    // MARK: - repeat / no-repeat (§3.6 anchor semantics)

    func testRepeatPhaseShiftsTheGridThroughTheAnchorAndCoversTheArea() {
        // anchor 10, tile 30: grid …,−20,10,40,70,… — first tile starts
        // left of 0 so the area edge is covered (spec: pattern is
        // infinite, clipped to the painting area).
        let plan = BackgroundTileMath.axisPlan(area: 100, tile: 30, anchor: 10, mode: .repeat)
        assertOrigins([-20, 10, 40, 70], plan)
        // Full coverage: first ≤ 0 and last + tile ≥ area.
        XCTAssertLessThanOrEqual(plan.origins.first!, 0)
        XCTAssertGreaterThanOrEqual(plan.origins.last! + plan.tileSize, 100)
    }

    func testNoRepeatDrawsASingleTileAtTheAnchor() {
        assertOrigins([35],
                      BackgroundTileMath.axisPlan(area: 100, tile: 30, anchor: 35, mode: .noRepeat))
    }

    // MARK: - degenerate inputs

    func testZeroTileOrAreaYieldsNoOrigins() {
        XCTAssertTrue(BackgroundTileMath.axisPlan(area: 100, tile: 0, anchor: 0, mode: .repeat).origins.isEmpty)
        XCTAssertTrue(BackgroundTileMath.axisPlan(area: 0, tile: 50, anchor: 0, mode: .space).origins.isEmpty)
    }

    // MARK: - keyword vocabulary

    func testModeKeywordMapping() {
        // The §3.7 words; nil / unknown normalize to the CSS initial.
        XCTAssertEqual(BackgroundTileMath.mode("no-repeat"), .noRepeat)
        XCTAssertEqual(BackgroundTileMath.mode("space"), .space)
        XCTAssertEqual(BackgroundTileMath.mode("round"), .round)
        XCTAssertEqual(BackgroundTileMath.mode("repeat"), .repeat)
        XCTAssertEqual(BackgroundTileMath.mode(nil), .repeat)
        XCTAssertEqual(BackgroundTileMath.mode("bogus"), .repeat)
    }

    // MARK: - fixture numbers (background-repeat.json: 30px tile, 200×120 box)

    func testFixtureSpaceAxes() {
        // x: floor(200/30)=6 tiles, leftover 20 → 4px gaps, flush edges.
        assertOrigins([0, 34, 68, 102, 136, 170],
                      BackgroundTileMath.axisPlan(area: 200, tile: 30, anchor: 0, mode: .space))
        // y: floor(120/30)=4 tiles, zero leftover → zero gaps.
        assertOrigins([0, 30, 60, 90],
                      BackgroundTileMath.axisPlan(area: 120, tile: 30, anchor: 0, mode: .space))
    }

    func testFixtureRoundAxes() {
        // x: 200/30 = 6.67 → 7 tiles of 200/7 ≈ 28.571 (rescaled).
        let x = BackgroundTileMath.axisPlan(area: 200, tile: 30, anchor: 0, mode: .round)
        XCTAssertEqual(x.tileSize, 200.0 / 7.0, accuracy: 0.001)
        XCTAssertEqual(x.origins.count, 7)
        // y: 120/30 = 4 exactly → untouched 30px tiles.
        let y = BackgroundTileMath.axisPlan(area: 120, tile: 30, anchor: 0, mode: .round)
        XCTAssertEqual(y.tileSize, 30, accuracy: 0.001)
        assertOrigins([0, 30, 60, 90], y)
    }

    /// The full lattice the Canvas paints for the fixture's `space` row:
    /// 6×4 = 24 rects, corners flush with the 200×120 box.
    func testFixtureSpaceLatticeThroughPlacement() {
        let plan = BackgroundImageGeometry.Placement(
            tileSize: CGSize(width: 30, height: 30), origin: .zero,
            repeatX: .space, repeatY: .space)
        let rects = BackgroundImageGeometry.tileRects(placement: plan,
                                                      box: CGSize(width: 200, height: 120))
        XCTAssertEqual(rects.count, 24)
        XCTAssertEqual(rects.first, CGRect(x: 0, y: 0, width: 30, height: 30))
        // Last tile flush with both far edges: 200−30=170, 120−30=90.
        XCTAssertEqual(rects.last!.origin.x, 170, accuracy: 0.01)
        XCTAssertEqual(rects.last!.origin.y, 90, accuracy: 0.01)
    }

    /// `round` rescales each axis independently in the emitted rects.
    func testFixtureRoundLatticeRescalesPerAxis() {
        let plan = BackgroundImageGeometry.Placement(
            tileSize: CGSize(width: 30, height: 30), origin: .zero,
            repeatX: .round, repeatY: .round)
        let rects = BackgroundImageGeometry.tileRects(placement: plan,
                                                      box: CGSize(width: 200, height: 120))
        XCTAssertEqual(rects.count, 7 * 4)
        // Width refit to 200/7; height stays 30 (exact 4× fit).
        XCTAssertEqual(rects.first!.width, 200.0 / 7.0, accuracy: 0.001)
        XCTAssertEqual(rects.first!.height, 30, accuracy: 0.001)
    }

    // MARK: - gradient tile sizing (§3.9, intrinsic-less images)

    func testGradientTileSizeKeywordsResolveToTheBox() {
        let box = CGSize(width: 160, height: 80)
        // No intrinsic dimensions → auto/cover/contain all fill the area.
        XCTAssertEqual(BackgroundImageGeometry.gradientTileSize(box: box, size: nil), box)
        XCTAssertEqual(BackgroundImageGeometry.gradientTileSize(box: box, size: .auto), box)
        XCTAssertEqual(BackgroundImageGeometry.gradientTileSize(box: box, size: .cover), box)
        XCTAssertEqual(BackgroundImageGeometry.gradientTileSize(box: box, size: .contain), box)
    }

    func testGradientTileSizeExplicitAxes() {
        let box = CGSize(width: 160, height: 80)
        // `background-size: 100px` — the omitted axis is auto → 100% of
        // the area for an image with no intrinsic proportions (NOT the
        // raster ratio-preserving rule).
        XCTAssertEqual(BackgroundImageGeometry.gradientTileSize(
                            box: box, size: .explicit(w: .px(100), h: .auto)),
                       CGSize(width: 100, height: 80))
        // `50%` single value → 80×80 (percent of the box, auto height).
        XCTAssertEqual(BackgroundImageGeometry.gradientTileSize(
                            box: box, size: .explicit(w: .percent(50), h: .auto)),
                       CGSize(width: 80, height: 80))
        // Two explicit values, px and percent mixes.
        XCTAssertEqual(BackgroundImageGeometry.gradientTileSize(
                            box: box, size: .explicit(w: .px(100), h: .px(50))),
                       CGSize(width: 100, height: 50))
        XCTAssertEqual(BackgroundImageGeometry.gradientTileSize(
                            box: box, size: .explicit(w: .percent(50), h: .percent(100))),
                       CGSize(width: 80, height: 80))
    }

    // MARK: - applier routing (default path must stay untouched)

    func testGradientNeedsGeometryRouting() {
        // No knobs at all → default full-box GradientApplier path.
        XCTAssertFalse(BackgroundImageApplier.gradientNeedsGeometry(
            size: nil, positionX: nil, positionY: nil, repeatLayer: nil))
        // Keyword sizes equal the box for gradients → still default.
        XCTAssertFalse(BackgroundImageApplier.gradientNeedsGeometry(
            size: .cover, positionX: nil, positionY: nil, repeatLayer: nil))
        // Initial repeat on both axes changes nothing → still default.
        XCTAssertFalse(BackgroundImageApplier.gradientNeedsGeometry(
            size: nil, positionX: nil, positionY: nil,
            repeatLayer: BackgroundRepeatLayer(x: "repeat", y: "repeat")))
        // Explicit size shrinks the tile → geometry path.
        XCTAssertTrue(BackgroundImageApplier.gradientNeedsGeometry(
            size: .explicit(w: .px(30), h: .px(30)),
            positionX: nil, positionY: nil, repeatLayer: nil))
        // Any position (px offsets shift even full-box tiles) → geometry.
        XCTAssertTrue(BackgroundImageApplier.gradientNeedsGeometry(
            size: nil, positionX: .px(20), positionY: nil, repeatLayer: nil))
        // Non-initial repeat on one axis → geometry.
        XCTAssertTrue(BackgroundImageApplier.gradientNeedsGeometry(
            size: nil, positionX: nil, positionY: nil,
            repeatLayer: BackgroundRepeatLayer(x: "space", y: "repeat")))
    }
}
