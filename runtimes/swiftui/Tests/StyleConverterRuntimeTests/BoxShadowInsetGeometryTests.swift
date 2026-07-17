//
//  BoxShadowInsetGeometryTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the Lane S inset box-shadow rewrite. The old iOS approximation
//  stroked a CENTERED ring at the element edge and offset the blurred
//  ring — leaving an opaque colour band on all four edges regardless of
//  the layer's (x, y). The rewrite mirrors Android's punched-hole
//  geometry (ShadowApplier.applyInsetShadows): fill a slab inflated by
//  blur+spread, subtract the border-box shape offset by (x, y) and inset
//  by spread (even-odd), blur by blur/2, mask to the element shape.
//  These tests pin the pure geometry (InsetShadowGeometry) and the
//  even-odd point membership of the punched path (InsetShadowShape).
//

import Foundation
import SwiftUI
import XCTest
// @testable: InsetShadowGeometry / InsetShadowShape are internal.
@testable import StyleConverterRuntime

final class BoxShadowInsetGeometryTests: XCTestCase {

    // Convenience: 100×80 border box at the origin, matching how the
    // Shape receives its rect from SwiftUI.
    private let bounds = CGRect(x: 0, y: 0, width: 100, height: 80)

    // ── Geometry parameters ─────────────────────────────────────────

    func testGeometryForOffsetBlurSpreadLayer() {
        // inset 4px 6px 10px 3px — the full-parameter case.
        let layer = BoxShadowLayer(x: 4, y: 6, blur: 10, spread: 3, inset: true)
        let g = InsetShadowGeometry.compute(bounds: bounds, layer: layer)
        // Slab pad = blur + max(spread, 0) + 50 = 63 on every side
        // (mirrors Android's outerPadding derivation).
        XCTAssertEqual(g.outerRect, bounds.insetBy(dx: -63, dy: -63))
        // Hole rect = border box shifted by the CSS offset — the shadow
        // band must land on the top/left for a positive (x, y).
        XCTAssertEqual(g.holeRect, CGRect(x: 4, y: 6, width: 100, height: 80))
        // Spread shrinks the hole via BorderRadiusShape.inset(by:).
        XCTAssertEqual(g.holeInset, 3)
        // CSS blur r ⇒ Gaussian σ = r/2 ⇒ SwiftUI .blur(radius: 5).
        XCTAssertEqual(g.blurRadius, 5)
    }

    func testNegativeSpreadGrowsHoleAndSkipsPad() {
        // Negative spread must GROW the hole (shadow retreats outward)
        // and must not shrink the slab pad below blur + 50.
        let layer = BoxShadowLayer(x: 0, y: 0, blur: 10, spread: -4, inset: true)
        let g = InsetShadowGeometry.compute(bounds: bounds, layer: layer)
        // pad = 10 + max(-4, 0) + 50 = 60 — spread's negative sign is
        // clamped out of the bleed distance.
        XCTAssertEqual(g.outerRect, bounds.insetBy(dx: -60, dy: -60))
        // The raw spread still flows to inset(by:) so the hole grows.
        XCTAssertEqual(g.holeInset, -4)
    }

    func testZeroBlurIsCrisp() {
        // inset 0 0 0 5px — hard-edged inner ring, no Gaussian at all.
        let layer = BoxShadowLayer(x: 0, y: 0, blur: 0, spread: 5, inset: true)
        let g = InsetShadowGeometry.compute(bounds: bounds, layer: layer)
        XCTAssertEqual(g.blurRadius, 0)
        XCTAssertEqual(InsetShadowGeometry.blurRadius(for: layer), 0)
    }

    // ── Even-odd path membership ────────────────────────────────────
    // The punched path must colour edge bands and leave the hole empty —
    // the exact failure the stroked-ring approximation had (opaque band
    // on ALL four edges regardless of offset).

    // Build the punched path for a square-cornered element.
    private func punchedPath(_ layer: BoxShadowLayer) -> Path {
        InsetShadowShape(box: BorderRadiusShape(radius: BorderRadiusConfig()),
                         layer: layer).path(in: bounds)
    }

    func testOffsetLayerFillsOnlyTopLeftBands() {
        // inset 8px 8px 0 0: hole = (8, 8, 100, 80). Under even-odd fill
        // the shadow colour covers ONLY the 8px top and left bands inside
        // the element; centre and bottom/right edges are inside the hole.
        let path = punchedPath(BoxShadowLayer(x: 8, y: 8, blur: 0, spread: 0, inset: true))
        // Top band (y < 8) and left band (x < 8) are filled.
        XCTAssertTrue(path.contains(CGPoint(x: 50, y: 4), eoFill: true))
        XCTAssertTrue(path.contains(CGPoint(x: 4, y: 40), eoFill: true))
        // Centre is inside the hole → unfilled.
        XCTAssertFalse(path.contains(CGPoint(x: 50, y: 40), eoFill: true))
        // Bottom/right edges sit inside the SHIFTED hole → unfilled —
        // this is precisely where the old ring wrongly painted a band.
        XCTAssertFalse(path.contains(CGPoint(x: 50, y: 76), eoFill: true))
        XCTAssertFalse(path.contains(CGPoint(x: 96, y: 40), eoFill: true))
    }

    func testSpreadOnlyLayerFillsUniformRing() {
        // inset 0 0 0 6px: the hole shrinks 6px on every side, so all
        // four 6px edge bands fill — symmetric, unlike the offset case.
        let path = punchedPath(BoxShadowLayer(x: 0, y: 0, blur: 0, spread: 6, inset: true))
        XCTAssertTrue(path.contains(CGPoint(x: 50, y: 3), eoFill: true))   // top
        XCTAssertTrue(path.contains(CGPoint(x: 50, y: 77), eoFill: true))  // bottom
        XCTAssertTrue(path.contains(CGPoint(x: 3, y: 40), eoFill: true))   // left
        XCTAssertTrue(path.contains(CGPoint(x: 97, y: 40), eoFill: true))  // right
        XCTAssertFalse(path.contains(CGPoint(x: 50, y: 40), eoFill: true)) // centre
    }
}
