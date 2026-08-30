//
//  PercentInsetResolveTests.swift
//  Wave 49 (lane A7) — pins for PERCENTAGE INSETS
//  (CSS 2.1 §9.4.3 + css-position-3 §relpos-insets).
//
//  Every payload below is copied VERBATIM out of the frozen wave-48
//  per-test IR the runtimes actually consumed:
//    tools/titan/runs/wave48-final/sections/css-position/per-test-ir/
//      wpt__css-position__position-relative-00{1,2,6,8}.json
//  so the wire shape asserted here is the shape the corpus produces.
//  Those 7 css-position tests are the ONLY components in all 30 frozen
//  sections whose inset data is a bare JSON number.
//
//  The rule table is the twin of Compose's PercentInsetResolveTest.kt —
//  same decisions, same numbers, so a cross-native probe can diff them.
//

import XCTest
@testable import StyleConverterRuntime

final class PercentInsetResolveTests: XCTestCase {

    /// Decode IR properties the way the runtime does.
    private func props(_ entries: String...) throws -> [IRProperty] {
        let json = #"{"id":"c","name":"c","properties":[\#(entries.joined(separator: ","))]}"#
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8)).properties
    }

    private func aggregate(_ entries: String...) throws -> LayoutAggregate {
        let json = #"{"id":"c","name":"c","properties":[\#(entries.joined(separator: ","))]}"#
        let parsed = try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8)).properties
        var agg = LayoutAggregate()
        PositionExtractor.contribute(parsed, into: &agg)
        return agg
    }

    // MARK: - P1: the wire discriminator

    /// InsetValueSerializer emits IRPercentage as a bare scalar — this is
    /// literally position-relative-006's Top payload.
    func testP1BareNumberIsThePercentageWire() throws {
        let p = try props(#"{"type":"Top","data":-10000}"#)[0]
        XCTAssertEqual(PercentInsetResolve.percentOf(p.data), -10000)
    }

    /// The length / auto / calc shapes must never enter the channel —
    /// 2932 of the 2953 inset payloads in the frozen sections are one of
    /// the two object length shapes.
    func testP1LengthShapesStayOffTheChannel() throws {
        for payload in [#"{"px":-100}"#,
                        #"{"type":"length","px":100}"#,
                        #"\#("\"auto\"")"#,
                        #"{"expr":"calc(anchor(bottom) + 5px)"}"#] {
            let p = try props(#"{"type":"Top","data":\#(payload)}"#)[0]
            XCTAssertNil(PercentInsetResolve.percentOf(p.data), "payload: \(payload)")
        }
    }

    // MARK: - P2: extraction records the percentage without moving the pt slot

    /// Verbatim: per-test-ir/wpt__css-position__position-relative-006.json,
    /// component `position-relative-006__1__0` (the GREEN square).
    func testP2Relative006ChildExtractsPercentAndLegacyPoints() throws {
        let agg = try aggregate(
            #"{"type":"Width","data":{"type":"length","px":100}}"#,
            #"{"type":"Height","data":{"type":"length","px":100}}"#,
            #"{"type":"Top","data":-10000}"#,
            #"{"type":"Position","data":"RELATIVE"}"#)
        XCTAssertEqual(agg.position, .relative)
        // The percentage is recorded…
        XCTAssertEqual(agg.percentInsets?.top, -10000)
        // …and the point slot keeps the pre-wave-49 number-as-points
        // reading, so nothing that cannot see a containing block moves.
        XCTAssertEqual(agg.inset?.top, -10000)
    }

    /// Verbatim: position-relative-001's inner GREEN div.
    func testP2PxInsetNeverPopulatesTheChannel() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"RELATIVE"}"#,
            #"{"type":"Top","data":{"px":-100}}"#,
            #"{"type":"Left","data":{"px":-100}}"#)
        XCTAssertNil(agg.percentInsets)
        XCTAssertEqual(agg.inset?.top, -100)
    }

    /// ZIndex shares the bare-number wire. If it leaked into the channel
    /// the composed lane would fire on every z-indexed element.
    func testP2BareNumberZIndexIsNotAnInset() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"ABSOLUTE"}"#,
            #"{"type":"ZIndex","data":5}"#)
        XCTAssertNil(agg.percentInsets)
        XCTAssertEqual(agg.zIndex, 5)
    }

    // MARK: - P3: the repair — an indefinite block axis resolves to zero

    /// The parent (`wpt__…__position-relative-006__1`, the RED box)
    /// declares Width:100px and MIN-height:100px — no `height` — so the
    /// renderer publishes containingBlockWidth 100 / containingBlockHeight
    /// nil. css-position-3 §relpos-insets: a percentage against an
    /// indefinite dimension resolves to ZERO, which is exactly what the
    /// test's own <meta name=assert> demands.
    func testP3Relative006ResolvesTopToZero() throws {
        let agg = try aggregate(
            #"{"type":"Top","data":-10000}"#,
            #"{"type":"Position","data":"RELATIVE"}"#)
        let out = PercentInsetResolve.resolve(legacy: agg.inset,
                                              percents: agg.percentInsets,
                                              cbWidth: 100, cbHeight: nil)
        XCTAssertEqual(out?.top, 0)
    }

    /// position-relative-001's relatively-positioned `<span>`: `top:100%;
    /// left:100%` inside a 100×100 red div → cb (100, 100). Both axes are
    /// definite so the used insets are +100/+100 — the SAME numbers the
    /// legacy number-as-points path produced, which is why 001/003/004/005
    /// are byte-neutral under this change.
    func testP3DefiniteAxesScaleToTheSameNumbers() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"RELATIVE"}"#,
            #"{"type":"Top","data":100}"#,
            #"{"type":"Left","data":100}"#)
        let out = PercentInsetResolve.resolve(legacy: agg.inset,
                                              percents: agg.percentInsets,
                                              cbWidth: 100, cbHeight: 100)
        XCTAssertEqual(out?.top, 100)
        XCTAssertEqual(out?.left, 100)
    }

    /// Guards against the change degenerating back into "number as points":
    /// 25% of a 200pt containing block is 50pt, not 25pt.
    func testP3FractionalPercentageActuallyScales() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"ABSOLUTE"}"#,
            #"{"type":"Top","data":25}"#,
            #"{"type":"Left","data":25}"#)
        let out = PercentInsetResolve.resolve(legacy: agg.inset,
                                              percents: agg.percentInsets,
                                              cbWidth: 200, cbHeight: 400)
        XCTAssertEqual(out?.top, 100)   // 25% of the 400pt block axis
        XCTAssertEqual(out?.left, 50)   // 25% of the 200pt inline axis
    }

    // MARK: - P4: the channel-gap guard

    /// position-relative-002's GREEN div: its parent is an inline `<span>`
    /// with no declared size, so the channel publishes (nil, nil). That nil
    /// means "we never modelled the real containing block" (CSS 2.1 §10.1
    /// puts it at the nearest BLOCK CONTAINER ancestor, which the channel
    /// skipped) — not "CSS-indefinite". Keeping the legacy value is the
    /// honest degradation; it can never move a box that renders correctly.
    func testP4NoDefiniteAxisLeavesTheRectAlone() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"RELATIVE"}"#,
            #"{"type":"Top","data":-100}"#,
            #"{"type":"Left","data":-100}"#)
        let out = PercentInsetResolve.resolve(legacy: agg.inset,
                                              percents: agg.percentInsets,
                                              cbWidth: nil, cbHeight: nil)
        XCTAssertEqual(out, agg.inset)
        XCTAssertEqual(out?.top, -100)
        XCTAssertEqual(out?.left, -100)
    }

    /// position-relative-008's `<tr>` carries `top: 100%` and its parent
    /// `<tbody>` has NO properties at all → (nil, nil). Untouched — that
    /// test passes on all three platforms today and must keep doing so.
    func testP4Relative008TbodyParentHitsTheSameGuard() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"RELATIVE"}"#,
            #"{"type":"Top","data":100}"#)
        XCTAssertEqual(PercentInsetResolve.resolve(legacy: agg.inset,
                                                   percents: agg.percentInsets,
                                                   cbWidth: nil, cbHeight: nil),
                       agg.inset)
    }

    func testP4NoPercentagesReturnsTheRectIdentically() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"RELATIVE"}"#,
            #"{"type":"Top","data":{"px":40}}"#)
        XCTAssertEqual(PercentInsetResolve.resolve(legacy: agg.inset,
                                                   percents: agg.percentInsets,
                                                   cbWidth: 100, cbHeight: 100),
                       agg.inset)
    }

    // MARK: - P5: axis assignment across all eight longhands

    /// CSS 2.1 §9.4.3 axis table: right/left (+ inset-inline-*) scale the
    /// containing block's WIDTH; top/bottom (+ inset-block-*) its HEIGHT.
    func testP5PhysicalLonghandsTakeTheRightAxis() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"ABSOLUTE"}"#,
            #"{"type":"Top","data":10}"#, #"{"type":"Right","data":10}"#,
            #"{"type":"Bottom","data":10}"#, #"{"type":"Left","data":10}"#)
        let out = PercentInsetResolve.resolve(legacy: agg.inset,
                                              percents: agg.percentInsets,
                                              cbWidth: 200, cbHeight: 400)
        XCTAssertEqual(out?.top, 40)     // 10% of 400
        XCTAssertEqual(out?.bottom, 40)  // 10% of 400
        XCTAssertEqual(out?.left, 20)    // 10% of 200
        XCTAssertEqual(out?.right, 20)   // 10% of 200
    }

    /// fixtures/properties/layout/inset-logical.json is the fixture that
    /// exercises this family. Horizontal-tb is the only writing mode the
    /// runtime models, so block = vertical.
    func testP5LogicalSpellingsTakeTheSameAxes() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"ABSOLUTE"}"#,
            #"{"type":"InsetBlockStart","data":10}"#,
            #"{"type":"InsetBlockEnd","data":10}"#,
            #"{"type":"InsetInlineStart","data":10}"#,
            #"{"type":"InsetInlineEnd","data":10}"#)
        XCTAssertNotNil(agg.percentInsets)
        let out = PercentInsetResolve.resolve(legacy: agg.inset,
                                              percents: agg.percentInsets,
                                              cbWidth: 200, cbHeight: 400)
        XCTAssertEqual(out?.top, 40)
        XCTAssertEqual(out?.bottom, 40)
        XCTAssertEqual(out?.left, 20)
        XCTAssertEqual(out?.right, 20)
    }

    /// `top: 50%; left: 10px` — only the percentage side is rewritten.
    func testP5MixedDeclarationKeepsItsPxSide() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"ABSOLUTE"}"#,
            #"{"type":"Top","data":50}"#,
            #"{"type":"Left","data":{"px":10}}"#)
        let out = PercentInsetResolve.resolve(legacy: agg.inset,
                                              percents: agg.percentInsets,
                                              cbWidth: 200, cbHeight: 400)
        XCTAssertEqual(out?.top, 200)
        XCTAssertEqual(out?.left, 10)
    }

    /// RTL mirrors the physical inline slots in lockstep with
    /// `InsetRect.resolved(isRTL:)`, or a `left: 50%` would scale onto the
    /// wrong physical side.
    func testP5RtlMirrorsTheInlineSlots() throws {
        let agg = try aggregate(
            #"{"type":"Position","data":"ABSOLUTE"}"#,
            #"{"type":"Left","data":25}"#)
        let mirrored = agg.percentInsets!.resolved(isRTL: true)
        XCTAssertNil(mirrored.left)
        XCTAssertEqual(mirrored.right, 25)
    }
}
