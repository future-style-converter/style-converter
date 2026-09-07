//
//  TransformOriginKeywordTests.swift
//  retro R5 (audit A11#14, iOS half) — two-keyword `transform-origin`.
//
//  css-transforms-1 §4's `&&` production (drafts.csswg.org ED numbering —
//  §5 in the TR/CR text; retro F2 re-cited) lets the two keywords appear in
//  either order; the horizontal word names x, the vertical word y. The
//  converter stores them positionally (`top right` → x:TOP, y:RIGHT) and
//  the extractor read them positionally, so `top right` pivoted about the
//  BOTTOM-LEFT corner. The wire payloads below are VERBATIM from the A11
//  fixture run (properties__transforms__transform-origin/ir.json,
//  fixtures/properties/transforms/transform-origin.json), and the geometry
//  pins reproduce the audit's MEASURED Chromium ink bboxes for the 120×80
//  box at (16,16) rotated 15° in the 390×112 capture:
//      003_Origin_TopRight      web [0,0,136,94]   (x0,y0,w,h)
//      004_Origin_BottomCenter  web [18,3,137,109]
//  and, as the negative witness, the iOS captures the bug produced:
//      003 iOS [16,19,137,93]  (= a rotation about the bottom-left corner)
//      004 iOS [9,0,138,95]    (= a rotation about the right-middle)
//

// SwiftUI for UnitPoint — the anchor type TransformOriginValue carries.
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class TransformOriginKeywordTests: XCTestCase {

    /// Extract the origin from a verbatim `TransformOrigin` data payload.
    private func origin(_ data: String) throws -> TransformOriginValue {
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion": 2, "minReaderVersion": 2, "components": [{"id": "c", "name": "c",
          "properties": [{"type": "TransformOrigin", "data": \(data)}]}]}
        """.utf8))
        return try XCTUnwrap(TransformsExtractor.extract(from: doc.components[0].properties)?.origin)
    }

    /// Inclusive ink extents [x0, y0, x1, y1] of the fixture's 120×80 box at
    /// (16,16) rotated by CSS rotate(15deg) about `origin` (0…1 fractions of
    /// the box), clipped to the 390×112 capture — the quantity A11's pngtool
    /// measured. A pixel column/row is covered when the box reaches into it:
    /// first = floor(min), last = ceil(max) − 1.
    private func rotatedInk(originX: CGFloat, originY: CGFloat) -> [Int] {
        let w: CGFloat = 120, h: CGFloat = 80, bx: CGFloat = 16, by: CGFloat = 16
        let ox = bx + originX * w, oy = by + originY * h
        // CSS rotate(θ) in a y-down frame: x' = x·cosθ − y·sinθ, y' = x·sinθ + y·cosθ.
        let th = 15 * CGFloat.pi / 180, c = cos(th), s = sin(th)
        var xs: [CGFloat] = [], ys: [CGFloat] = []
        for (px, py) in [(bx, by), (bx + w, by), (bx + w, by + h), (bx, by + h)] {
            let dx = px - ox, dy = py - oy
            xs.append(ox + dx * c - dy * s)
            ys.append(oy + dx * s + dy * c)
        }
        let minX = max(0, xs.min()!), maxX = min(390, xs.max()!)
        let minY = max(0, ys.min()!), maxY = min(112, ys.max()!)
        return [Int(floor(minX)), Int(floor(minY)), Int(ceil(maxX)) - 1, Int(ceil(maxY)) - 1]
    }

    /// Compare an analytic ink bbox with a MEASURED one, 1px per edge: a
    /// rotated corner tip covers only a sliver of its pixel (the bug's
    /// top corner sits at y = 18.73, 27% of row 18) and the measuring
    /// tool's colour tolerance decides whether that antialiased pixel
    /// counts. The pivot error being pinned moves edges by 16–17px, so
    /// the tolerance costs nothing.
    private func assertInk(_ got: [Int], _ measured: [Int], _ what: String,
                           file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(got.count, 4, what, file: file, line: line)
        for (g, m) in zip(got, measured) {
            XCTAssertLessThanOrEqual(abs(g - m), 1, "\(what): analytic \(got) vs measured \(measured)",
                                     file: file, line: line)
        }
    }

    func testTopRightPivotsAboutTheTopRightCorner() throws {
        // Verbatim wire for `transform-origin: top right`.
        let o = try origin(#"{"x": {"type": "keyword", "value": "TOP"}, "y": {"type": "keyword", "value": "RIGHT"}}"#)
        XCTAssertEqual(o.unit, UnitPoint(x: 1, y: 0), "RIGHT names x, TOP names y — order-free (§4 &&)")
        XCTAssertNil(o.xPx); XCTAssertNil(o.yPx)
        // Chromium ink bbox [0,0,136,94] ⇔ inclusive [0,0,135,93].
        assertInk(rotatedInk(originX: o.unit.x, originY: o.unit.y), [0, 0, 135, 93], "003 web")
    }

    func testBottomCenterPivotsAboutTheBottomMiddle() throws {
        // Verbatim wire for `transform-origin: bottom center`.
        let o = try origin(#"{"x": {"type": "keyword", "value": "BOTTOM"}, "y": {"type": "keyword", "value": "CENTER"}}"#)
        XCTAssertEqual(o.unit, UnitPoint(x: 0.5, y: 1), "BOTTOM names y; the vacated x slot is center")
        // Chromium ink bbox [18,3,137,109] ⇔ inclusive [18,3,154,111].
        assertInk(rotatedInk(originX: o.unit.x, originY: o.unit.y), [18, 3, 154, 111], "004 web")
    }

    func testTheGeometryHelperReproducesTheMeasuredBugToo() {
        // Negative witness: the pre-fix positional read made `top right`
        // (0,1) = bottom-left and `bottom center` (1,0.5) = right-middle.
        // The audit measured iOS at [16,19,137,93] and [9,0,138,95] — the
        // same inclusive extents this arithmetic predicts for those pivots,
        // which is what ties the fix to the Chromium numbers above.
        assertInk(rotatedInk(originX: 0, originY: 1), [16, 19, 152, 111], "003 iOS (bug)")
        assertInk(rotatedInk(originX: 1, originY: 0.5), [9, 0, 146, 94], "004 iOS (bug)")
    }

    func testCenterControlIsUnchanged() throws {
        // Verbatim wire for `transform-origin: center` (identical on all
        // three platforms in the audit run; ink ≈ [8,2,143,109] at tol 40).
        let o = try origin(#"{"x": {"type": "keyword", "value": "CENTER"}, "y": {"type": "keyword", "value": "CENTER"}}"#)
        XCTAssertEqual(o.unit, .center)
        // Measured at tolerance 40 on both web and iOS: (8, 2, 143, 109).
        assertInk(rotatedInk(originX: 0.5, originY: 0.5), [8, 2, 143, 109], "000 control")
    }

    func testPositionalOrderStaysPositional() throws {
        // `top left` — the same corner either way, both spellings.
        XCTAssertEqual(try origin(#"{"x": {"type": "keyword", "value": "LEFT"}, "y": {"type": "keyword", "value": "TOP"}}"#).unit,
                       UnitPoint(x: 0, y: 0))
        XCTAssertEqual(try origin(#"{"x": {"type": "keyword", "value": "TOP"}, "y": {"type": "keyword", "value": "LEFT"}}"#).unit,
                       UnitPoint(x: 0, y: 0))
        // `right center` and `center right` are the same point (§4 &&).
        XCTAssertEqual(try origin(#"{"x": {"type": "keyword", "value": "RIGHT"}, "y": {"type": "keyword", "value": "CENTER"}}"#).unit,
                       UnitPoint(x: 1, y: 0.5))
        XCTAssertEqual(try origin(#"{"x": {"type": "keyword", "value": "CENTER"}, "y": {"type": "keyword", "value": "RIGHT"}}"#).unit,
                       UnitPoint(x: 1, y: 0.5))
        // Verbatim `left` (single keyword): the converter emits LEFT, CENTER.
        XCTAssertEqual(try origin(#"{"x": {"type": "keyword", "value": "LEFT"}, "y": {"type": "keyword", "value": "CENTER"}}"#).unit,
                       UnitPoint(x: 0, y: 0.5))
    }

    func testALoneVerticalKeywordCentresTheHorizontalAxis() throws {
        // The converter duplicates a lone `top` into both slots; §4 makes the
        // omitted value `center`, so this is x:center, y:top — not (0,0).
        XCTAssertEqual(try origin(#"{"x": {"type": "keyword", "value": "TOP"}, "y": {"type": "keyword", "value": "TOP"}}"#).unit,
                       UnitPoint(x: 0.5, y: 0))
    }

    func testANumericBesideASwappedKeywordIsAnInvalidDeclaration() throws {
        // `top 10px` / `30% right`: the §4 two-value form only lets the
        // KEYWORD pair reorder, so these are invalid — Chromium drops the
        // declaration and the origin stays at the initial `50% 50%`. Neither
        // the keyword's axis nor the number may survive (the old resolver
        // kept the keyword; Compose kept the number — three answers on three
        // platforms). Synthetic wire: no corpus or fixture carrier exists.
        let a = try origin(#"{"x": {"type": "keyword", "value": "TOP"}, "y": {"type": "length", "px": 10}}"#)
        XCTAssertEqual(a.unit, .center, "`top 10px` is not in the grammar — initial value")
        XCTAssertNil(a.xPx); XCTAssertNil(a.yPx, "the dropped <length> must not ride as a px anchor")
        let b = try origin(#"{"x": {"type": "percentage", "percentage": 30}, "y": {"type": "keyword", "value": "RIGHT"}}"#)
        XCTAssertEqual(b.unit, .center, "`30% right` is not in the grammar — initial value")
        // The breadcrumb was left (logOnce is false once the key exists).
        XCTAssertFalse(PropertyTracker.logOnce(key: "transform-origin-grammar", message: "dup"))
        // Control: the VALID orders keep their number — `10px top` and
        // `right 10px` fit `[<lp>|h-kw] [<lp>|v-kw]` positionally.
        let c = try origin(#"{"x": {"type": "length", "px": 10}, "y": {"type": "keyword", "value": "TOP"}}"#)
        XCTAssertEqual(c.xPx, 10); XCTAssertEqual(c.unit.y, 0)
        let d = try origin(#"{"x": {"type": "keyword", "value": "RIGHT"}, "y": {"type": "length", "px": 10}}"#)
        XCTAssertEqual(d.unit.x, 1); XCTAssertEqual(d.yPx, 10)
    }

    func testAFiftyPercentBesideASwappedKeywordIsDroppedLikeCompose() throws {
        // Retro round-2 F2 (skeptic S4 defect 0) — the twin-probe table. A
        // `50%` beside a reordered keyword is a <percentage>, not the
        // `center` keyword: the converter emits {type:percentage,percentage:
        // 50} for it and {type:keyword,value:CENTER} for `center`, so the
        // wire distinguishes them and §4's keyword-only `&&` rule applies —
        // Chromium drops `top 50%` and `50% right` to the initial 50% 50%,
        // and Compose (TransformOriginKeywords.dropsDeclaration) already did.
        // The old resolver tested `!= Axis.center`, which read this 50% as
        // the implicit centre and gave (0.5, 0) / (1, 0.5): iOS alone.
        // Synthetic wires (S4-edge components to_top50pct / to_50pctright);
        // no corpus or fixture carrier exists, so no cell moves.
        let a = try origin(#"{"x": {"type": "keyword", "value": "TOP"}, "y": {"type": "percentage", "percentage": 50}}"#)
        XCTAssertEqual(a.unit, .center, "`top 50%` is not in the grammar — initial value, as on Compose")
        let b = try origin(#"{"x": {"type": "percentage", "percentage": 50}, "y": {"type": "keyword", "value": "RIGHT"}}"#)
        XCTAssertEqual(b.unit, .center, "`50% right` is not in the grammar — initial value, as on Compose")
        // Control — the KEYWORD centre beside the same reordered keywords is
        // the valid `&&` pair and keeps its axes (already pinned above, held
        // here so the two wire shapes sit side by side).
        XCTAssertEqual(try origin(#"{"x": {"type": "keyword", "value": "TOP"}, "y": {"type": "keyword", "value": "CENTER"}}"#).unit,
                       UnitPoint(x: 0.5, y: 0))
        XCTAssertEqual(try origin(#"{"x": {"type": "keyword", "value": "CENTER"}, "y": {"type": "keyword", "value": "RIGHT"}}"#).unit,
                       UnitPoint(x: 1, y: 0.5))
    }

    func testNumericAxesAreUntouched() throws {
        // Verbatim `20px 40px` and `30px 25%` shapes ride as px / fraction.
        let px = try origin(#"{"x": {"type": "length", "px": 20}, "y": {"type": "length", "px": 40}}"#)
        XCTAssertEqual(px.xPx, 20); XCTAssertEqual(px.yPx, 40)
        let mixed = try origin(#"{"x": {"type": "length", "px": 30}, "y": {"type": "percentage", "percentage": 25}}"#)
        XCTAssertEqual(mixed.xPx, 30); XCTAssertNil(mixed.yPx)
        XCTAssertEqual(mixed.unit.y, 0.25, accuracy: 1e-9)
    }
}
