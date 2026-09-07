//
//  FidelityWave5Tests.swift
//  StyleConverterRuntimeTests
//
//  Wave-5 pins — the placement-tree cluster:
//    1. GridPlacer.resolveNames — css-grid-1 §8.3 named-line resolution
//       against grid-template-areas, the dangling-name implicit-line
//       rule, and negative-integer normalisation. Expected cells are
//       BROWSER-VERIFIED (puppeteer probe over the exact CSS the web
//       runtime emits for the fidelity/placement fixtures).
//    2. ItemPlacementExtractor — named grid lines ride the claim raw
//       (the container is the only party that can resolve them).
//    3. TransformsMath-adjacent: TranslateEffect resolves percentage
//       translate against the element's OWN size (css-transforms-1 §6),
//       not the parent proposal the old GeometryReader leaked in.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class FidelityWave5Tests: XCTestCase {

    // MARK: - Named-line resolution (css-grid-1 §8.3)

    /// PL_AreasCard template: "media title" / "media body".
    private let cardAreas = [["media", "title"], ["media", "body"]]

    func testAreaStartNameResolvesToAreaFirstRowLine() {
        // `grid-row-start: media` → the media-start implicit line = row 1.
        // Column stays AUTO — the browser does NOT copy the area's
        // column (verified: media renders 80×40 in row 1 col 1 only,
        // never spanning the template's two rows).
        var rq = GridItemRequest()
        rq.rowStartName = "media"
        let r = GridPlacer.resolveNames(rq, areas: cardAreas,
                                        columnCount: 2, explicitRowCount: 2)
        XCTAssertEqual(r.rowStart, 1)
        XCTAssertNil(r.colStart)          // column untouched → auto
        XCTAssertNil(r.rowEnd)            // end untouched → span 1
    }

    func testAreaEndNameResolvesToLinePastArea() {
        // `grid-row-end: media` → media-end line = 3 (area covers rows 1–2).
        var rq = GridItemRequest()
        rq.rowEndName = "media"
        let r = GridPlacer.resolveNames(rq, areas: cardAreas,
                                        columnCount: 2, explicitRowCount: 2)
        XCTAssertEqual(r.rowEnd, 3)
    }

    func testColumnNamesResolveAgainstAreaColumns() {
        // "head head head" / "side main main": `grid-column-start: main`
        // → col line 2; `grid-column-end: main` → col line 4.
        let areas = [["head", "head", "head"], ["side", "main", "main"]]
        var rq = GridItemRequest()
        rq.colStartName = "main"
        rq.colEndName = "main"
        let r = GridPlacer.resolveNames(rq, areas: areas,
                                        columnCount: 3, explicitRowCount: 2)
        XCTAssertEqual(r.colStart, 2)
        XCTAssertEqual(r.colEnd, 4)
    }

    func testDanglingNameLandsOnFirstImplicitLine() {
        // PL_AreasDangling `ghost`: no such area → §8.3.1 assumes every
        // implicit line carries the name. Explicit grid of 2 rows owns
        // lines 1…3, so ghost = line 4 — browser-verified: ghost sits in
        // implicit row 4 leaving an EMPTY implicit row 3 (the web capture
        // shows the 6px gap + 0px row between `b/c` and `ghost`).
        var rq = GridItemRequest()
        rq.rowStartName = "ghost"
        let r = GridPlacer.resolveNames(rq, areas: [["a", "a"], ["b", "c"]],
                                        columnCount: 2, explicitRowCount: 2)
        XCTAssertEqual(r.rowStart, 4)
    }

    func testNegativeLinesCountFromExplicitEnd() {
        // `grid-column-end: -1` on a 3-column grid → the last explicit
        // line (4) — PL_LineSpans `c` spans the full row (§8.3).
        var rq = GridItemRequest()
        rq.colStart = 1
        rq.colEnd = -1
        let r = GridPlacer.resolveNames(rq, areas: nil,
                                        columnCount: 3, explicitRowCount: 0)
        XCTAssertEqual(r.colEnd, 4)
        // Rows mirror the rule against the explicit row count.
        var rr = GridItemRequest()
        rr.rowEnd = -1
        let r2 = GridPlacer.resolveNames(rr, areas: [["x"], ["y"]],
                                         columnCount: 1, explicitRowCount: 2)
        XCTAssertEqual(r2.rowEnd, 3)
    }

    func testNoNamesIsIdentity() {
        // Numeric-only requests pass through untouched (no template).
        var rq = GridItemRequest()
        rq.colStart = 2
        rq.rowStart = 3
        rq.rowSpan = 2
        XCTAssertEqual(GridPlacer.resolveNames(rq, areas: nil,
                                               columnCount: 3,
                                               explicitRowCount: 0), rq)
    }

    // MARK: - End-to-end: resolved names through the §8.5 placer

    /// PL_AreasCard end-to-end: three children carrying ONLY a named
    /// grid-row-start (the converter's expansion of `grid-area: <name>`)
    /// land exactly where the browser puts them.
    func testAreasCardPlacementMatchesBrowser() {
        let names = ["media", "title", "body"]
        let reqs = names.map { n -> GridItemRequest in
            var rq = GridItemRequest()
            rq.rowStartName = n
            return GridPlacer.resolveNames(rq, areas: cardAreas,
                                           columnCount: 2, explicitRowCount: 2)
        }
        let (cells, rows) = GridPlacer.assign(reqs, columnCount: 2)
        // media → row 1 col 1 (NO row span — see the §8.3 pin above).
        XCTAssertEqual(cells[0], GridCell(col: 0, row: 0, colSpan: 1, rowSpan: 1))
        // title → row 1 col 2 (row-locked sparse placement after media).
        XCTAssertEqual(cells[1], GridCell(col: 1, row: 0, colSpan: 1, rowSpan: 1))
        // body → row 2 col 1 (its row restarts the column scan).
        XCTAssertEqual(cells[2], GridCell(col: 0, row: 1, colSpan: 1, rowSpan: 1))
        XCTAssertEqual(rows, 2)
    }

    /// PL_InterleaveAreas: named children definite-row-lock rows 1 and 2;
    /// the two unclaimed children auto-flow into the remaining row-1
    /// cells (they must NOT be dropped — the wave-5 headline bug).
    func testInterleaveAreasKeepsUnclaimedChildren() {
        let areas = [["top", "top", "top"], [".", "mid", "."]]
        func named(_ n: String) -> GridItemRequest {
            var rq = GridItemRequest()
            rq.rowStartName = n
            return GridPlacer.resolveNames(rq, areas: areas,
                                           columnCount: 3, explicitRowCount: 2)
        }
        let reqs = [named("top"), named("mid"), GridItemRequest(), GridItemRequest()]
        let (cells, _) = GridPlacer.assign(reqs, columnCount: 3)
        XCTAssertEqual(cells[0], GridCell(col: 0, row: 0, colSpan: 1, rowSpan: 1)) // top
        XCTAssertEqual(cells[1], GridCell(col: 0, row: 1, colSpan: 1, rowSpan: 1)) // mid
        XCTAssertEqual(cells[2], GridCell(col: 1, row: 0, colSpan: 1, rowSpan: 1)) // u1
        XCTAssertEqual(cells[3], GridCell(col: 2, row: 0, colSpan: 1, rowSpan: 1)) // u2
    }

    // MARK: - Claim carriage (extractor)

    func testExtractorCarriesNamedLinesRaw() throws {
        // `grid-area: media` reaches iOS as GridRowStart{type:name} —
        // the claim carries the NAME; no premature resolution.
        let props = try JSONDecoder().decode([IRProperty].self, from: Data("""
        [ {"type":"GridRowStart","data":{"type":"name","name":"media"}},
          {"type":"GridColumnEnd","data":{"type":"name","name":"side"}} ]
        """.utf8))
        let p = ItemPlacementExtractor.extract(from: props)
        XCTAssertEqual(p.grid.request.rowStartName, "media")
        XCTAssertEqual(p.grid.request.colEndName, "side")
        XCTAssertNil(p.grid.request.rowStart)   // numbers stay auto
        XCTAssertNil(p.grid.request.colEnd)
    }

    // MARK: - Gradient geometry + colour space (css-images-3)

    func testLinearEndpointsPixelSpace() {
        // §3.1.1 on a NON-SQUARE box: 45deg over 200×80 must keep the
        // 45° pixel direction (the old unit-square math squashed it to
        // ≈22°). Gradient-line length = |w·sin| + |h·cos| ≈ 197.99.
        let (s, e) = GradientApplier.linearEndpoints(
            angleDeg: 45, size: CGSize(width: 200, height: 80))
        // Pixel-space delta between endpoints must sit at 45°: dx = dy.
        let dxPx = (e.x - s.x) * 200
        let dyPx = (e.y - s.y) * 80
        XCTAssertEqual(dxPx, 140.0, accuracy: 0.1)   // L·sin45 ≈ 140
        XCTAssertEqual(dyPx, -140.0, accuracy: 0.1)  // upward on screen
        // Cardinal sanity: 90deg = to right, whole width.
        let (s2, e2) = GradientApplier.linearEndpoints(
            angleDeg: 90, size: CGSize(width: 200, height: 80))
        XCTAssertEqual(s2.x, 0, accuracy: 0.001)
        XCTAssertEqual(e2.x, 1, accuracy: 0.001)
        XCTAssertEqual(s2.y, 0.5, accuracy: 0.001)
        // 180deg = to bottom (CSS default).
        let (s3, e3) = GradientApplier.linearEndpoints(
            angleDeg: 180, size: CGSize(width: 200, height: 80))
        XCTAssertEqual(s3.y, 0, accuracy: 0.001)
        XCTAssertEqual(e3.y, 1, accuracy: 0.001)
    }

    func testDefaultEllipseEndRadiusIsFarthestCorner() {
        // css-images-3 §3.2: the default radial ending shape is an
        // ellipse sized `farthest-corner` — farthest-SIDE aspect
        // (w/2 : h/2 centred) scaled to pass THROUGH the farthest
        // corner, i.e. radii (√2·w/2, √2·h/2). The applier renders a
        // circle on the max(w,h) square then squashes to the box
        // aspect, so the pre-squash radius must be √2·max(w,h)/2 (the
        // old max(w,h)/2 was farthest-side — fades ended 29% early on
        // every default radial vs web/Compose).
        let r = GradientApplier.ellipseEndRadius(CGSize(width: 200, height: 80))
        XCTAssertEqual(r, 100 * sqrt(2), accuracy: 0.001)
        // After the (w/m, h/m) squash the per-axis radii land on the
        // §3.5 values for the 200×80 box…
        let m: CGFloat = 200                          // squash-source square
        let rx = r * 200 / m, ry = r * 80 / m
        XCTAssertEqual(rx, sqrt(2) * 100, accuracy: 0.001)
        XCTAssertEqual(ry, sqrt(2) * 40, accuracy: 0.001)
        // …and the geometric ground truth: the farthest corner
        // (±w/2, ±h/2) from the centre lies exactly ON that ellipse.
        XCTAssertEqual(pow(100 / rx, 2) + pow(40 / ry, 2), 1, accuracy: 0.001)
    }

    func testSrgbSubdivisionPinsMidpoint() {
        // red→blue in sRGB passes (0.5, 0, 0.5) at the midpoint — the
        // browser's dark purple. (SwiftUI's own ramp would pass a washed
        // pink; the subdivision pins it to the sRGB line.)
        let raw = [GradientApplier.RGBAStop(r: 1, g: 0, b: 0, a: 1, loc: 0),
                   GradientApplier.RGBAStop(r: 0, g: 0, b: 1, a: 1, loc: 1)]
        let fine = GradientApplier.srgbSubdivided(raw, segments: 12)
        XCTAssertEqual(fine.count, 13)                 // 1 + 12 lerped
        let mid = fine[6]                              // t = 0.5
        XCTAssertEqual(mid.r, 0.5, accuracy: 0.001)
        XCTAssertEqual(mid.b, 0.5, accuracy: 0.001)
        XCTAssertEqual(mid.loc, 0.5, accuracy: 0.001)
    }

    // MARK: - Percentage translate (css-transforms-1 §6)

    // MARK: - True 3D rotation (css-transforms-2 §13.1)

    func testBareRotateYIsOrthographic() {
        // rotateY(60°) with NO perspective: pure |cos60| = 0.5 horizontal
        // scale about the centre — no keystone (m14 must stay 0).
        let fx = Rotate3DEffect(axisX: 0, axisY: 1, axisZ: 0, deg: 60,
                                perspectivePx: nil, anchor: .center)
        let m = fx.effectValue(size: CGSize(width: 180, height: 80))
        XCTAssertEqual(m.m11, 0.5, accuracy: 0.001)   // cos 60°
        XCTAssertEqual(m.m13, 0, accuracy: 0.0001)    // no projection
        XCTAssertEqual(m.m22, 1, accuracy: 0.001)     // Y untouched
        // Centre anchor: x' = 0.5x + 45 keeps the midpoint fixed.
        XCTAssertEqual(m.m31, 45, accuracy: 0.01)     // (1−cos60)·cx
    }

    func testPerspectiveRotateYProducesKeystone() {
        // perspective(500px) rotateY(45°): the projective column must be
        // non-zero (m13 in ProjectionTransform = the §13.1 w-row term),
        // sign such that the right edge recedes (positive rotation).
        let fx = Rotate3DEffect(axisX: 0, axisY: 1, axisZ: 0, deg: 45,
                                perspectivePx: 500, anchor: .center)
        let m = fx.effectValue(size: CGSize(width: 180, height: 96))
        XCTAssertNotEqual(m.m13, 0)                   // keystone present
        // Project a point through the full 2D-projective slice.
        func proj(_ x: CGFloat, _ y: CGFloat) -> CGFloat {
            let w = m.m13 * x + m.m23 * y + m.m33
            return (m.m11 * x + m.m21 * y + m.m31) / w
        }
        let cx: CGFloat = 90, cy: CGFloat = 48
        // The anchor is the projection's fixed point (w == 1 there).
        XCTAssertEqual(proj(cx, cy), cx, accuracy: 0.01)
        // Keystone orientation: positive rotateY sends the RIGHT edge
        // away (foreshortened) and brings the LEFT edge forward
        // (magnified) — the asymmetry browsers paint.
        let left = proj(0, cy), right = proj(180, cy)
        XCTAssertLessThan(left, cx)                    // order preserved
        XCTAssertGreaterThan(right, cx)
        XCTAssertGreaterThan(cx - left, right - cx)    // left magnified
    }

    func testTranslateEffectResolvesPercentAgainstOwnSize() {
        // translate: 50% 50% on a 200×48 box → (+100, +24) — the wave-5
        // fix: the old GeometryReader resolved against the ~390px canvas.
        let fx = TranslateEffect(x: 0, y: 0, xFrac: 0.5, yFrac: 0.5)
        let m = fx.effectValue(size: CGSize(width: 200, height: 48))
        XCTAssertEqual(m.m31, 100, accuracy: 0.001) // tx
        XCTAssertEqual(m.m32, 24, accuracy: 0.001)  // ty
        // Mixed absolute + percent: translate: 20px 50% on 180×96.
        let fx2 = TranslateEffect(x: 20, y: 0, xFrac: 0, yFrac: 0.5)
        let m2 = fx2.effectValue(size: CGSize(width: 180, height: 96))
        XCTAssertEqual(m2.m31, 20, accuracy: 0.001)
        XCTAssertEqual(m2.m32, 48, accuracy: 0.001)
    }
}
