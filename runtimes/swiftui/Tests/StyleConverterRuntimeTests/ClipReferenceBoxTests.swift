//
//  ClipReferenceBoxTests.swift
//  StyleConverterRuntimeTests — wave 46 (lane Y4).
//
//  Pins for css-masking-1 §5.1 reference-box resolution, the SVG path()
//  parser and the inset percent sides on iOS. Every expected number comes
//  from a wave45-final css-masking WPT test whose PNG showed the defect:
//   - contentBox-1a: `circle(farthest-side) content-box` on a 100px box
//     with 40px padding clipped the 180px border-box circle (ref: 100px
//     circle at the content box).
//   - geometryBox-2: `polygon(0% 75%, 50% 25%, 100% 75%) margin-box` on a
//     100px box with 50px margins kept only the roof (ref: house shape —
//     the polygon spans the 200px margin box).
//   - marginBox-1b/1c/1d: `clip-path: margin-box` was an identity no-op
//     (200px outline flooded the canvas); 1d's corner must be 34.4.
//   - path-001/002: `path(nonzero|evenodd, 'M10,10h80v80h-80zM25,25h50v50h-50z')`
//     — the old whitespace walker produced an EMPTY path (blank render).
//   - inset-round-percent: `inset(80% 0 0 round 8%)` clipped nothing.
//

import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class ClipReferenceBoxTests: XCTestCase {

    private func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private func px(_ v: Double) -> IRValue { obj(["px": .double(v)]) }
    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // The contentBox-1a wire (per-test-ir, wave45-final) minus colour.
    private var contentBox1a: [IRProperty] {
        props([
            ("Position", .string("ABSOLUTE")), ("Left", px(10)), ("Top", px(10)),
            ("Width", obj(["type": .string("length"), "px": .double(100)])),
            ("Height", obj(["type": .string("length"), "px": .double(100)])),
            ("PaddingTop", px(40)), ("PaddingRight", px(40)), ("PaddingBottom", px(40)), ("PaddingLeft", px(40)),
            ("ClipPath", obj(["geometry-box": .string("content-box"),
                              "shape": obj(["type": .string("circle"), "r": .string("farthest-side")])])),
        ])
    }

    func testGeometryBoxPlusShapeKeepsBothAndReadsTheMetrics() {
        let cfg = ClipExtractor.extract(from: contentBox1a)
        XCTAssertEqual(cfg?.geometryBox, .contentBox)
        if case .circle(_, let kind, _, _) = cfg?.shape ?? .none {
            XCTAssertEqual(kind, .farthestSide)
        } else { XCTFail("shape under geometry-box not unwrapped") }
        XCTAssertEqual(cfg?.box.paddingLeft, 40)
        XCTAssertEqual(cfg?.box.marginTop, 0)
    }

    func testContentBoxOfContentBox1aIsTheInner100pxSquare() {
        // The clip rect is the 180px border box; the content box sits
        // 40px in on every side.
        let cfg = ClipExtractor.extract(from: contentBox1a)!
        let f = ClipReferenceBox.resolve(cfg.geometryBox, metrics: cfg.box,
                                         in: CGRect(x: 0, y: 0, width: 180, height: 180))
        XCTAssertEqual(f.rect, CGRect(x: 40, y: 40, width: 100, height: 100))
    }

    func testBareGeometryBoxKeywordAndSvgOnlyMappings() {
        func box(_ kw: String) -> ClipGeometryBox? {
            ClipExtractor.extract(from: props([("ClipPath", obj(["geometry-box": .string(kw)]))]))?.geometryBox
        }
        XCTAssertEqual(box("margin-box"), .marginBox)
        XCTAssertEqual(box("padding-box"), .paddingBox)
        // css-masking-1 §5.1 used values for an element with a CSS box.
        XCTAssertEqual(box("fill-box"), .contentBox)
        XCTAssertEqual(box("stroke-box"), .borderBox)
        XCTAssertEqual(box("view-box"), .borderBox)
        if case .geometryBoxOnly = ClipExtractor.extract(
            from: props([("ClipPath", obj(["geometry-box": .string("margin-box")]))]))?.shape ?? .none {
        } else { XCTFail("bare keyword must yield geometryBoxOnly") }
    }

    func testMarginBoxOutsetsByTheDeclaredMargins() {
        // marginBox-1b: 50×50 box, margin 25 → the margin box is 100×100
        // starting 25px up and left of the border box, corners square.
        let cfg = ClipExtractor.extract(from: props([
            ("ClipPath", obj(["geometry-box": .string("margin-box")])),
            ("MarginTop", px(25)), ("MarginRight", px(25)), ("MarginBottom", px(25)), ("MarginLeft", px(25)),
        ]))!
        let f = ClipReferenceBox.resolve(cfg.geometryBox, metrics: cfg.box,
                                         in: CGRect(x: 0, y: 0, width: 50, height: 50))
        XCTAssertEqual(f.rect, CGRect(x: -25, y: -25, width: 100, height: 100))
        XCTAssertTrue(f.topLeft.isSquare)
    }

    func testMarginBoxCornerRule() {
        // marginBox-1d: radius 10, margin 50 → 10 + 50·(1 + (0.2 − 1)³) = 34.4.
        XCTAssertEqual(ClipReferenceBox.marginOutsetRadius(10, 50), 34.4, accuracy: 0.01)
        // marginBox-1c: 50px radius on a 50px box overlap-scales to 25,
        // ratio 1 → 25 + 25 = 50: the 100px margin box is a full circle.
        XCTAssertEqual(ClipReferenceBox.marginOutsetRadius(25, 25), 50, accuracy: 0.001)
        XCTAssertEqual(ClipReferenceBox.marginOutsetRadius(0, 25), 0, accuracy: 0.001)
        // Negative margin: r + m floored at 0.
        XCTAssertEqual(ClipReferenceBox.marginOutsetRadius(2, -5), 0, accuracy: 0.001)
    }

    func testMarginBox1cEndToEndIsACircle() {
        let cfg = ClipExtractor.extract(from: props([
            ("ClipPath", obj(["geometry-box": .string("margin-box")])),
            ("MarginTop", px(25)), ("MarginRight", px(25)), ("MarginBottom", px(25)), ("MarginLeft", px(25)),
            ("BorderTopLeftRadius", px(50)), ("BorderTopRightRadius", px(50)),
            ("BorderBottomRightRadius", px(50)), ("BorderBottomLeftRadius", px(50)),
        ]))!
        let f = ClipReferenceBox.resolve(cfg.geometryBox, metrics: cfg.box,
                                         in: CGRect(x: 0, y: 0, width: 50, height: 50))
        XCTAssertEqual(f.topLeft.x, 50, accuracy: 0.001)
        XCTAssertEqual(f.bottomRight.y, 50, accuracy: 0.001)
        // The rounded path is one closed contour that excludes the corner
        // pixel and includes the edge midpoints and the centre.
        let path = ClipReferenceBox.roundedPath(f)
        XCTAssertFalse(path.contains(CGPoint(x: -23, y: -23)))
        XCTAssertTrue(path.contains(CGPoint(x: 25, y: -24)))
        XCTAssertTrue(path.contains(CGPoint(x: 25, y: 25)))
        XCTAssertTrue(path.contains(CGPoint(x: 74, y: 25)))
        XCTAssertFalse(path.contains(CGPoint(x: 73, y: 73)))
    }

    func testPaddingAndContentBoxesInsetRadiiByBorderThenPadding() {
        // contentBox-1e: padding 4, border 4, border-radius 58 → content
        // box radius 58 − 4 − 4 = 50 on the 100px content box: a circle.
        let cfg = ClipExtractor.extract(from: props([
            ("ClipPath", obj(["geometry-box": .string("content-box")])),
            ("PaddingTop", px(4)), ("PaddingRight", px(4)), ("PaddingBottom", px(4)), ("PaddingLeft", px(4)),
            ("BorderTopWidth", px(4)), ("BorderRightWidth", px(4)), ("BorderBottomWidth", px(4)), ("BorderLeftWidth", px(4)),
            ("BorderTopStyle", .string("SOLID")), ("BorderRightStyle", .string("SOLID")),
            ("BorderBottomStyle", .string("SOLID")), ("BorderLeftStyle", .string("SOLID")),
            ("BorderTopLeftRadius", px(58)), ("BorderTopRightRadius", px(58)),
            ("BorderBottomRightRadius", px(58)), ("BorderBottomLeftRadius", px(58)),
        ]))!
        let border = CGRect(x: 0, y: 0, width: 116, height: 116)
        let padding = ClipReferenceBox.resolve(.paddingBox, metrics: cfg.box, in: border)
        XCTAssertEqual(padding.rect, CGRect(x: 4, y: 4, width: 108, height: 108))
        XCTAssertEqual(padding.topLeft.x, 54, accuracy: 0.001)
        let content = ClipReferenceBox.resolve(.contentBox, metrics: cfg.box, in: border)
        XCTAssertEqual(content.rect, CGRect(x: 8, y: 8, width: 100, height: 100))
        XCTAssertEqual(content.topLeft.y, 50, accuracy: 0.001)
    }

    func testNoneStyleBorderHasZeroUsedWidth() {
        // CSS2 §8.5.3: `border-top-style: none` → used width 0.
        let cfg = ClipExtractor.extract(from: props([
            ("ClipPath", obj(["geometry-box": .string("padding-box")])),
            ("BorderTopWidth", px(8)), ("BorderTopStyle", .string("NONE")),
        ]))!
        XCTAssertEqual(cfg.box.borderTop, 0)
    }

    func testNoMetricsKeepsEveryBoxEqualToTheRect() {
        // Byte-stability guarantee for the committed 047-050 baselines.
        let rect = CGRect(x: 3, y: 5, width: 120, height: 80)
        for box in [ClipGeometryBox.marginBox, .borderBox, .paddingBox, .contentBox] {
            let f = ClipReferenceBox.resolve(box, metrics: .none, in: rect)
            XCTAssertEqual(f.rect, rect)
            XCTAssertTrue(f.bottomLeft.isSquare)
        }
        XCTAssertEqual(ClipReferenceBox.roundedPath(
            ClipReferenceBox.resolve(.borderBox, metrics: .none, in: rect)).boundingRect, rect)
    }

    func testInsetPercentSidesSurfaceAsFractions() {
        let shape = ClipExtractor.extract(from: props([
            ("ClipPath", obj([
                "type": .string("inset"),
                "t": obj(["original": obj(["v": .double(80), "u": .string("PERCENT")])]),
                "r": px(0), "b": px(0), "l": px(0),
                "round": obj(["original": obj(["v": .double(8), "u": .string("PERCENT")])]),
            ])),
        ]))?.shape
        if case .inset(let sides, _, let frac) = shape ?? .none {
            XCTAssertEqual(sides.topFraction ?? -1, 0.8, accuracy: 0.0001)
            XCTAssertNil(sides.rightFraction)
            XCTAssertEqual(sides.top, 0)
            XCTAssertEqual(frac ?? -1, 0.08, accuracy: 0.0001)
        } else { XCTFail("inset not extracted") }
    }

    func testPathFillRuleRidesTheFunctionNotClipRule() {
        // clip-path-path-002: `path(evenodd, …)` with no clip-rule.
        let cfg = ClipExtractor.extract(from: props([
            ("ClipPath", obj(["type": .string("path"), "rule": .string("evenodd"),
                              "d": .string("M10,10h80v80h-80zM25,25h50v50h-50z")])),
        ]))
        XCTAssertEqual(cfg?.pathFillRule, .evenodd)
        XCTAssertEqual(cfg?.rule, .nonzero)
    }

    // MARK: - SvgPathParser

    func testGluedSvgDataParsesToTheTwoSquares() {
        // path-001's data: verbs glued to numbers, comma separators,
        // relative h/v, negative numbers, two subpaths.
        let p = SvgPathParser.parse("M10,10h80v80h-80zM25,25h50v50h-50z")
        XCTAssertNotNil(p)
        XCTAssertEqual(p!.boundingRect, CGRect(x: 10, y: 10, width: 80, height: 80))
        // Nonzero: the inner square is filled (both wound the same way).
        XCTAssertTrue(p!.contains(CGPoint(x: 50, y: 50)))
        XCTAssertTrue(p!.contains(CGPoint(x: 15, y: 15)))
        XCTAssertFalse(p!.contains(CGPoint(x: 5, y: 5)))
        // Even-odd: the inner square is a hole (path-002's hollow rect).
        XCTAssertFalse(p!.contains(CGPoint(x: 50, y: 50), eoFill: true))
        XCTAssertTrue(p!.contains(CGPoint(x: 15, y: 15), eoFill: true))
    }

    func testSpacedAbsoluteDataAndImplicitRepeats() {
        // path-with-zoom: `M0,0 L100,0  L0,100  L0,0` (double spaces).
        let tri = SvgPathParser.parse("M0,0 L100,0  L0,100  L0,0")!
        XCTAssertEqual(tri.boundingRect, CGRect(x: 0, y: 0, width: 100, height: 100))
        XCTAssertTrue(tri.contains(CGPoint(x: 20, y: 20)))
        XCTAssertFalse(tri.contains(CGPoint(x: 80, y: 80)))
        // Implicit repeats: `L 10 0 10 10` is two linetos; `M` repeats are linetos.
        let rep = SvgPathParser.parse("M0 0 10 0 10 10 Z")!
        XCTAssertEqual(rep.boundingRect, CGRect(x: 0, y: 0, width: 10, height: 10))
        // Juxtaposed numbers / exponents: `1.5.5` is 1.5 then .5; `1e1` is 10.
        let odd = SvgPathParser.parse("M0 0L1e1 0L1.5.5Z")!
        XCTAssertEqual(odd.boundingRect.width, 10, accuracy: 0.0001)
        XCTAssertEqual(odd.boundingRect.height, 0.5, accuracy: 0.0001)
    }

    func testCurvesAndArcs() {
        // Quadratic + cubic + smooth variants keep the bounding box sane.
        let q = SvgPathParser.parse("M 10 10 Q 80 -20 150 10 L 150 110 Q 80 140 10 110 Z")!
        XCTAssertEqual(q.boundingRect.minX, 10, accuracy: 0.0001)
        XCTAssertEqual(q.boundingRect.maxX, 150, accuracy: 0.0001)
        XCTAssertLessThan(q.boundingRect.minY, 10)
        // Arc: SVG §8.3.8 sweep-flag 1 = positive-angle direction, which
        // in y-down user space runs left → top → right: the half circle
        // from (0,0) to (100,0) of radius 50 bulges UP to y = −50 (the
        // classic `M10 50 A40 40 0 0 1 90 50` hill). Sweep 0 mirrors it.
        let a = SvgPathParser.parse("M0 0 A50 50 0 0 1 100 0 Z")!
        XCTAssertEqual(a.boundingRect.minY, -50, accuracy: 0.5)
        XCTAssertEqual(a.boundingRect.maxY, 0, accuracy: 0.5)
        let b = SvgPathParser.parse("M0 0 A50 50 0 0 0 100 0 Z")!
        XCTAssertEqual(b.boundingRect.maxY, 50, accuracy: 0.5)
        // Juxtaposed arc flags (`01`) parse as two flags.
        XCTAssertNotNil(SvgPathParser.parse("M0 0 a50 50 0 01 100 0"))
    }

    func testMalformedDataDoesNotClipEverything() {
        // No drawable command → nil (the applier then clips nothing).
        XCTAssertNil(SvgPathParser.parse(""))
        XCTAssertNil(SvgPathParser.parse("hello"))
        // Truncated argument list keeps what was drawn before it.
        let partial = SvgPathParser.parse("M0 0 L10 0 L10")!
        XCTAssertEqual(partial.boundingRect.width, 10, accuracy: 0.0001)
    }
}
