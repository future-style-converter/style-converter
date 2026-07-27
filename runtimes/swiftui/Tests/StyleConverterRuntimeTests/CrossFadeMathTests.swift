//
//  CrossFadeMathTests.swift
//  StyleConverterRuntimeTests — wave-21 IMAGES lane.
//
//  PIN TABLE — cross-fade() weight normalization (css-images-4 §2.6.2).
//  The SAME rows exist in the byte-parallel twins:
//    runtimes/web/tests/background/BackgroundImageCrossFadeCenter.test.ts
//    runtimes/compose/src/test/java/com/styleconverter/runtime/background/CrossFadeMathTest.kt
//  Any edit here must be mirrored in both — the three engines must share
//  one normalization or their composited alphas drift apart.
//

import XCTest
@testable import StyleConverterRuntime

final class CrossFadeMathTests: XCTestCase {

    // Exact-equality helper: pure double arithmetic — the twins pin
    // identical outputs without epsilon fuzz.
    private func check(_ input: [Double?], _ expected: [Double],
                       file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(CrossFadeMath.normalizeWeights(input), expected,
                       file: file, line: line)
    }

    func testSixTenPercentLayersKeepTenthEach() {
        // cross-fade-target-alpha.html: sum 60 ≤ 100, none omitted — the
        // 40% remainder renders transparent (total alpha 0.6).
        check(Array(repeating: 10.0, count: 6), Array(repeating: 0.10, count: 6))
    }

    func testTwoOmittedWeightsSplitEvenly() {
        // cross-fade-premultiplied-alpha.html: no percentages authored.
        check([nil, nil], [0.5, 0.5])
    }

    func testLegacyTwentyFivePercentPair() {
        // Older two-arg syntax: the parser stores both weights explicitly.
        check([75, 25], [0.75, 0.25])
    }

    func testOver100SumsScaleDownProportionally() {
        // §2.6.2 rule: 150+50=200 → ×(100/200) → 75/25.
        check([150, 50], [0.75, 0.25])
    }

    func testOmittedWeightsShareFlooredRemainder() {
        // 60 specified + 2 omitted → remainder 40 → 20 each.
        check([60, nil, nil], [0.6, 0.2, 0.2])
    }

    func testSpecifiedOver100WithOmissionsGivesZero() {
        // Remainder floors at 0 (§2.6.2); the specified entry scales to 1.
        check([120, nil], [1.0, 0.0])
    }
}
