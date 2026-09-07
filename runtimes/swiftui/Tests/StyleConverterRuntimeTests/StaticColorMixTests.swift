//
//  StaticColorMixTests.swift
//  StyleConverterRuntimeTests — wave-49 lane A5.
//
//  Static `color-mix()` evaluation (css-color-5 §3). Every wire payload is
//  copied VERBATIM out of the wave-48 corpus run:
//    tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
//      wpt__css-color__color-mix-currentcolor-001.json  (in srgb, currentColor)
//      wpt__css-color__color-mix-currentcolor-002.json  (in lch,  currentColor)
//      wpt__css-color__color-mix-percents-01.json       (in lch, purple/plum,
//                                                        all six percent forms)
//      wpt__css-color__color-mix-percents-02.json       (125% / 9999% stripes)
//
//  The expected colours are NOT fitted to our output. They are the frozen
//  reference PNG's pixels and the values the WPT tests assert of themselves:
//    color-mix(in lch, green 50%, blue) = sRGB (0, 117, 189), the pixel at
//      (50,50) of tools/wpt/refs/…/css-color/color-mix-currentcolor-002.png;
//    every valid color-mix-percents-01 stripe = the .t1 control's
//      rgb(68.4898% 36.015% 68.3102%) → (174.65, 91.84, 174.19).
//

import XCTest
@testable import StyleConverterRuntime

final class StaticColorMixTests: XCTestCase {

    // MARK: - Wire helpers

    /// Parse a color-mix call written as JSON text, exactly as it appears on
    /// the wire, into the IRValue the extractor would hand over.
    private func mix(_ json: String) -> [String: IRValue] {
        let data = Data(("{\"original\":" + json + "}").utf8)
        let value = try! JSONDecoder().decode(IRValue.self, from: data)
        return StaticColorMix.payload(value)!
    }

    /// sRGB byte comparison — the unit the reference PNGs are stored in.
    private func assertBytes(
        _ expected: (Int, Int, Int), _ actual: ColorValue?,
        tol: Int = 1, file: StaticString = #filePath, line: UInt = #line
    ) {
        guard case .srgb(let r, let g, let b, _)? = actual else {
            return XCTFail("expected a resolved sRGB colour, got \(String(describing: actual))",
                           file: file, line: line)
        }
        let got = (Int((r * 255).rounded()), Int((g * 255).rounded()), Int((b * 255).rounded()))
        XCTAssertTrue(
            abs(got.0 - expected.0) <= tol && abs(got.1 - expected.1) <= tol
                && abs(got.2 - expected.2) <= tol,
            "rgb\(got) vs expected rgb\(expected)", file: file, line: line)
    }

    /// The purple every valid color-mix-percents-01 stripe must land on.
    private let percentsPurple = (175, 92, 174)

    private let green = (r: 0.0, g: 0.5019607843137255, b: 0.0, a: 1.0)
    private let red = (r: 1.0, g: 0.0, b: 0.0, a: 1.0)

    // MARK: - §6.4 substitution

    func testSrgbMixWithCurrentColorTakesTheElementsOwnColor() {
        // color-mix-currentcolor-001's CHILD receives the parent's mix through
        // `background-color: inherit` and re-resolves it against its own
        // green: color-mix(in srgb, green 50%, green) = green.
        assertBytes((0, 128, 0), StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"srgb","color1":"currentColor","percent1":50,"color2":"green"}"#),
            currentColor: green))
    }

    func testSrgbMixOnTheRedParentIsChromesBrown() {
        // Same wire on the PARENT (color: red). Chrome 151 computes
        // color(srgb 0.5 0.25098 0), measured with getComputedStyle.
        assertBytes((128, 64, 0), StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"srgb","color1":"currentColor","percent1":50,"color2":"green"}"#),
            currentColor: red))
    }

    func testLchMixWithCurrentColorMatchesTheReferencePixel() {
        // color-mix-currentcolor-002's child: color-mix(in lch, green 50%,
        // blue). Chrome 151: lch(37.9213 99.596 217.874); reference pixel
        // rgb(0,117,189).
        assertBytes((0, 117, 189), StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"currentColor","percent1":50,"color2":"blue"}"#),
            currentColor: green))
    }

    func testCurrentColorEndpointWithNoOwnColourStaysUnresolved() {
        XCTAssertNil(StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"srgb","color1":"currentColor","percent1":50,"color2":"green"}"#),
            currentColor: nil))
    }

    // MARK: - Percentage normalisation (css-color-5 §3.2)
    // All six valid forms of color-mix-percents-01 must give ONE colour.

    func testBothPercentagesGiven() {
        assertBytes(percentsPurple, StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":50,"color2":"plum","percent2":50}"#),
            currentColor: nil))
    }

    func testSecondPercentageOmitted() {
        assertBytes(percentsPurple, StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":50,"color2":"plum"}"#),
            currentColor: nil))
    }

    func testFirstPercentageOmitted() {
        assertBytes(percentsPurple, StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"purple","color2":"plum","percent2":50}"#),
            currentColor: nil))
    }

    func testBothOmittedDefaultToFiftyFifty() {
        assertBytes(percentsPurple, StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"purple","color2":"plum"}"#),
            currentColor: nil))
    }

    func testEndpointOrderIsSymmetric() {
        // …-01's .t6 swaps the operands; the §13.5 hue arc must be symmetric.
        assertBytes(percentsPurple, StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"plum","color2":"purple"}"#),
            currentColor: nil))
    }

    func testSumAboveOneHundredRenormalisesWithoutTouchingAlpha() {
        // …-01's .t7: 80% + 80% → 50/50; §2.1 only scales alpha BELOW 100%.
        let out = StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":80,"color2":"plum","percent2":80}"#),
            currentColor: nil)
        assertBytes(percentsPurple, out)
        guard case .srgb(_, _, _, let a)? = out else { return XCTFail("no colour") }
        XCTAssertEqual(a, 1, accuracy: 0.0001)
    }

    func testSumBelowOneHundredScalesAlpha() {
        let out = StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":25,"color2":"plum","percent2":25}"#),
            currentColor: nil)
        assertBytes(percentsPurple, out)
        guard case .srgb(_, _, _, let a)? = out else { return XCTFail("no colour") }
        XCTAssertEqual(a, 0.5, accuracy: 0.0001)
    }

    func testOutOfRangePercentagesAreInvalid() {
        // color-mix-percents-02's .t6/.t7. The grammar is
        // <percentage [0,100]>: out of range invalidates the declaration and
        // is NOT clamped — clamping would paint the 9999% stripe the same
        // purple as the valid ones and hide a real divergence.
        XCTAssertNil(StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":125,"color2":"plum","percent2":125}"#),
            currentColor: nil))
        XCTAssertNil(StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":9999,"color2":"plum","percent2":9999}"#),
            currentColor: nil))
    }

    func testZeroSumIsInvalid() {
        XCTAssertNil(StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":0,"color2":"plum","percent2":0}"#),
            currentColor: nil))
    }

    // MARK: - Refusals

    func testUnreadableEndpointIsRefused() {
        XCTAssertNil(StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"srgb","color1":"var(--x)","color2":"green"}"#),
            currentColor: nil))
    }

    func testUnsupportedSpaceIsRefused() {
        // display-p3 is grammatical but outside GradientColorMath's table;
        // mixing in sRGB instead would paint a plausible wrong colour.
        XCTAssertNil(StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"display-p3","color1":"red","color2":"blue"}"#),
            currentColor: nil))
    }

    func testPayloadOnlyClaimsColorMixValues() {
        func value(_ s: String) -> IRValue {
            try! JSONDecoder().decode(IRValue.self, from: Data(s.utf8))
        }
        XCTAssertNil(StaticColorMix.payload(nil))
        XCTAssertNil(StaticColorMix.payload(value(#"{"original":"currentColor"}"#)))
        XCTAssertNil(StaticColorMix.payload(
            value(#"{"original":{"type":"light-dark","lightColor":"red","darkColor":"blue"}}"#)))
        XCTAssertNil(StaticColorMix.payload(value(#"{"srgb":{"r":1,"g":0,"b":0}}"#)))
        XCTAssertNotNil(StaticColorMix.payload(
            value(#"{"original":{"type":"color-mix","colorSpace":"srgb","color1":"red","color2":"blue"}}"#)))
    }

    func testSrgbMixMatchesTheDocumentedPurple() {
        // The value the Compose twin's pre-existing srgb decoder documents:
        // color-mix(in srgb, red 50%, blue) = rgb(128,0,128). Pinning it on
        // both platforms keeps the two mixers from drifting apart.
        assertBytes((128, 0, 128), StaticColorMix.resolve(
            mix(#"{"type":"color-mix","colorSpace":"srgb","color1":"red","percent1":50,"color2":"blue"}"#),
            currentColor: nil))
    }
}
