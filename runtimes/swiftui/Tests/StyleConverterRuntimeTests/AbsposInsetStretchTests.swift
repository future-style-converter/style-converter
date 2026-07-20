//
//  AbsposInsetStretchTests.swift
//  Wave 18 (RC2, lane 1) — abspos inset-stretch sizing pins
//  (css-position-3 §3.5 + the css-sizing-4 §5 aspect-ratio interaction).
//
//  The resolver is pure math with an identical signature on Compose
//  (AbsposInsetStretchTest.kt pins the same table entry-for-entry so
//  cross-native probes can diff the rule tables); the config fold is
//  pinned against the LIVE wave-18 IR shapes
//  (tools/titan/runs/wave18-gate/sections/css-sizing/per-test-ir).
//

import XCTest
@testable import StyleConverterRuntime

final class AbsposInsetStretchTests: XCTestCase {

    /// Shorthand for the pure resolver with the common defaults.
    private func resolve(
        cbW: Double? = nil, cbH: Double? = nil,
        left: Double? = nil, right: Double? = nil,
        top: Double? = nil, bottom: Double? = nil,
        explicitW: Double? = nil, explicitH: Double? = nil,
        hasExplicitW: Bool? = nil, hasExplicitH: Bool? = nil,
        ratio: Double? = nil
    ) -> AbsposInsetStretch.Resolved {
        AbsposInsetStretch.resolve(
            cbW: cbW, cbH: cbH, left: left, right: right, top: top, bottom: bottom,
            explicitW: explicitW, explicitH: explicitH,
            hasExplicitW: hasExplicitW ?? (explicitW != nil),
            hasExplicitH: hasExplicitH ?? (explicitH != nil),
            ratio: ratio)
    }

    // MARK: - The failing-test pins (numbers straight from the live IRs)

    /// abspos-003: all-zero insets in the 100×500 relative parent with
    /// ratio 1 → 100×100 (inline stretch wins; block DERIVES from the
    /// ratio, never the 500px block stretch).
    func testAbspos003AllZeroInsetsRatioOneResolves100x100() {
        let r = resolve(cbW: 100, cbH: 500,
                        left: 0, right: 0, top: 0, bottom: 0, ratio: 1)
        XCTAssertEqual(r.widthPx, 100)
        XCTAssertEqual(r.heightPx, 100)
    }

    /// abspos-004 first box: left/top/bottom 0 in a 300×50 cb, ratio 2 →
    /// block stretch 50, fully-auto inline derives 50×2 = 100.
    func testAbspos004BlockStretchDerivesWidth() {
        let r = resolve(cbW: 300, cbH: 50, left: 0, top: 0, bottom: 0, ratio: 2)
        XCTAssertEqual(r.widthPx, 100)
        XCTAssertEqual(r.heightPx, 50)
    }

    /// abspos-004 second box: left/right/top 0 in a 100×300 cb, ratio 2 →
    /// inline stretch 100, block derives 100/2 = 50 (the physical-axis
    /// rule coincides with the test's vertical-lr expectation —
    /// documented approximation).
    func testAbspos004InlineStretchDerivesHeight() {
        let r = resolve(cbW: 100, cbH: 300, left: 0, right: 0, top: 0, ratio: 2)
        XCTAssertEqual(r.widthPx, 100)
        XCTAssertEqual(r.heightPx, 50)
    }

    // MARK: - Rule-table pins

    /// No ratio: each opposing-inset axis stretches independently.
    func testNoRatioStretchesBothAxes() {
        let r = resolve(cbW: 200, cbH: 300,
                        left: 10, right: 30, top: 20, bottom: 20)
        XCTAssertEqual(r.widthPx, 160)
        XCTAssertEqual(r.heightPx, 260)
    }

    /// A lone inset never stretches without a ratio to derive through.
    func testSingleInsetAxisNeverStretches() {
        let r = resolve(cbW: 200, cbH: 300, left: 10)
        XCTAssertNil(r.widthPx)
        XCTAssertNil(r.heightPx)
    }

    /// The S4 identity guard: abspos-001/002's explicit-size + ratio +
    /// no-inset shape resolves to NOTHING — the existing SizeApplier
    /// aspect-ratio path (already correct) stays untouched.
    func testExplicitSizePlusRatioWithoutInsetsIsIdentity() {
        let r = resolve(cbW: 358, cbH: 600, explicitW: 100, ratio: 1)
        XCTAssertNil(r.widthPx)
        XCTAssertNil(r.heightPx)
    }

    /// Explicit width + block stretch: the ratio overrides the stretch
    /// (height 100), the author width is never injected.
    func testRatioOverridesBlockStretchNextToExplicitWidth() {
        let r = resolve(cbW: 100, cbH: 500, top: 0, bottom: 0,
                        explicitW: 100, ratio: 1)
        XCTAssertNil(r.widthPx)
        XCTAssertEqual(r.heightPx, 100)
    }

    /// An explicit height blocks the ratio derivation of that axis; the
    /// inline stretch still injects.
    func testExplicitHeightBlocksDerivation() {
        let r = resolve(cbW: 200, cbH: 300, left: 0, right: 0,
                        explicitH: 80, ratio: 2)
        XCTAssertEqual(r.widthPx, 200)
        XCTAssertNil(r.heightPx)
    }

    /// An unknown containing-block axis disables the stretch on that
    /// axis only (nothing honest to resolve against).
    func testUnknownCbAxisDisablesThatStretch() {
        let r = resolve(cbW: 200, cbH: nil,
                        left: 0, right: 0, top: 0, bottom: 0)
        XCTAssertEqual(r.widthPx, 200)
        XCTAssertNil(r.heightPx)
    }

    /// Over-constrained insets clamp at zero, never negative
    /// (css-position-3 §3.5.3's floor).
    func testOverConstrainedClampsAtZero() {
        let r = resolve(cbW: 100, left: 80, right: 80)
        XCTAssertEqual(r.widthPx, 0)
    }

    // MARK: - The config fold (live wire shapes end-to-end)

    /// Decode the LIVE abspos-003 child shape through the real
    /// StyleBuilder + LayoutExtractor lanes, then fold — proving the
    /// wire → InsetRect → resolver path end-to-end.
    func testResolveForFoldsTheLiveAbspos003Shape() throws {
        let json = #"""
        {"id":"c","name":"c","properties":[
          {"type":"AspectRatio","data":{"ratio":{"w":1,"h":1},"normalizedRatio":1}},
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"Left","data":{"px":0}},
          {"type":"Right","data":{"px":0}},
          {"type":"Top","data":{"px":0}},
          {"type":"Bottom","data":{"px":0}}
        ]}
        """#
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
        // The SAME engine lanes the renderer uses: size via StyleBuilder,
        // insets via LayoutExtractor.
        let style = StyleBuilder.build(from: comp.properties)
        let r = AbsposInsetStretch.resolveFor(
            size: style.size,
            inset: LayoutExtractor.extract(from: comp.properties)?.inset,
            cbW: 100, cbH: 500)
        XCTAssertEqual(r.widthPx, 100)
        XCTAssertEqual(r.heightPx, 100)
    }

    /// Author sizes survive the fold untouched: the live abspos-001 root
    /// shape (explicit width + ratio, no insets) folds to the identity.
    func testResolveForIsIdentityForTheLiveAbspos001Shape() throws {
        let json = #"""
        {"id":"c","name":"c","properties":[
          {"type":"Width","data":{"type":"length","px":100}},
          {"type":"AspectRatio","data":{"ratio":{"w":1,"h":1},"normalizedRatio":1}},
          {"type":"Position","data":"ABSOLUTE"}
        ]}
        """#
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
        let style = StyleBuilder.build(from: comp.properties)
        let r = AbsposInsetStretch.resolveFor(
            size: style.size,
            inset: LayoutExtractor.extract(from: comp.properties)?.inset,
            cbW: 358, cbH: 600)
        XCTAssertNil(r.widthPx)
        XCTAssertNil(r.heightPx)
    }

    // MARK: - Wave-18 skeptic pins: the S1 honesty rule on the LIVE wire

    /// Pinned against the running converter (2026-07): `left: 10%` emits
    /// {"type":"Left","data":10.0} — a BARE number the permissive offset
    /// lane reads as px. S1 promises percent insets conservatively
    /// DISABLE the stretch, so strictInsets must refuse the bare shape
    /// (without it the axis stretched to cb − 10 − 30 as if px).
    func testPercentInsetBareNumberWireDisablesTheStretchAxis() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"c","name":"c","properties":[
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"Left","data":10.0},
          {"type":"Right","data":{"px":30}}
        ]}
        """#.utf8))
        let style = StyleBuilder.build(from: comp.properties)
        let r = AbsposInsetStretch.resolveFor(
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 500, cbH: 400)
        XCTAssertNil(r.widthPx)
        XCTAssertNil(r.heightPx)
    }

    /// Logical typed-px insets on both inline sides reach the stretch:
    /// cb 500 − 10 − 30 = 460 (matches the Compose twin's pin).
    func testLogicalPxInsetsStretchTheInlineAxis() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"c","name":"c","properties":[
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"InsetInlineStart","data":{"px":10.0}},
          {"type":"InsetInlineEnd","data":{"px":30.0}}
        ]}
        """#.utf8))
        let style = StyleBuilder.build(from: comp.properties)
        let r = AbsposInsetStretch.resolveFor(
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 500, cbH: 400)
        XCTAssertEqual(r.widthPx, 460)
        XCTAssertNil(r.heightPx)
    }

    /// A physical percent inset must NOT fall through to a logical px
    /// inset (the applier offsets by the physical side) — axis disabled.
    func testPhysicalPercentDoesNotFallThroughToLogicalPx() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"c","name":"c","properties":[
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"Left","data":10.0},
          {"type":"InsetInlineStart","data":{"px":5.0}},
          {"type":"Right","data":{"px":30}}
        ]}
        """#.utf8))
        let style = StyleBuilder.build(from: comp.properties)
        let r = AbsposInsetStretch.resolveFor(
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 500, cbH: 400)
        XCTAssertNil(r.widthPx)
    }

    /// Keyword `auto` on the physical side IS the initial value and falls
    /// through to the logical longhand: 400 − 20 − 30 = 350.
    func testAutoPhysicalFallsThroughToLogical() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"c","name":"c","properties":[
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"Top","data":"auto"},
          {"type":"InsetBlockStart","data":{"px":20.0}},
          {"type":"Bottom","data":{"px":30}}
        ]}
        """#.utf8))
        let style = StyleBuilder.build(from: comp.properties)
        let r = AbsposInsetStretch.resolveFor(
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 500, cbH: 400)
        XCTAssertEqual(r.heightPx, 350)
    }
}
