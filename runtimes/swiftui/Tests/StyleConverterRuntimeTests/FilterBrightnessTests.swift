//
//  FilterBrightnessTests.swift
//  Regression pins for the CSS brightness() operator.
//
//  filter-effects-1 §2.2 defines brightness() as a linear MULTIPLIER on the
//  colour channels. SwiftUI's `.brightness(_:)` is an ADDITIVE shift in
//  [-1, 1]. The two coincide only at the identity point, and FilterApplier
//  used the additive one — mapping `pct` to `(pct - 100) / 100`.
//
//  Measured on fixtures/visual-test.json's Filter_Brightness
//  (#2ecc71 = (46,204,113) under brightness(150)) across all three runtimes:
//
//      multiplicative  (46,204,113) x 1.5      -> (69, 255, 170)
//      additive        (46,204,113) + 0.5*255  -> (174, 255, 240)
//
//      web      (69, 255, 170)   correct
//      Android  (69, 255, 170)   correct
//      iOS      (174, 255, 241)  the additive result   <- the bug
//
//  After the fix iOS captures (69, 255, 169) — one LSB of rounding off the
//  reference, and matching the other two runtimes.
//
//  These tests pin the ARITHMETIC rather than the rendering: the factor is
//  what was wrong, and it is checkable without a device.
//

import XCTest
@testable import StyleConverterRuntime

final class FilterBrightnessTests: XCTestCase {

    /// The exact defect: 150 must mean "x1.5", not "+0.5".
    func testBrightnessIsMultiplicativeNotAdditive() {
        XCTAssertEqual(FilterApplier.brightnessFactor(150), 1.5, accuracy: 1e-12)
        // The additive mapping the old code used would give 0.5 here. If this
        // ever equals 0.5 again, brightness() has regressed to SwiftUI's
        // parameter space.
        XCTAssertNotEqual(FilterApplier.brightnessFactor(150), 0.5, accuracy: 1e-12)
    }

    /// 100 is the identity for a multiplier — the one point where the old
    /// additive mapping happened to agree, which is why the bug survived.
    func testIdentityAtOneHundred() {
        XCTAssertEqual(FilterApplier.brightnessFactor(100), 1.0, accuracy: 1e-12)
    }

    /// 0 is black. Under the additive mapping this was -1, which SwiftUI
    /// also renders as black — the second point of accidental agreement.
    func testZeroIsBlack() {
        XCTAssertEqual(FilterApplier.brightnessFactor(0), 0.0, accuracy: 1e-12)
    }

    /// Halving. The additive mapping gave -0.5 (a shift), which darkens by a
    /// constant instead of scaling — visibly different on any mid-tone.
    func testHalfBrightness() {
        XCTAssertEqual(FilterApplier.brightnessFactor(50), 0.5, accuracy: 1e-12)
    }

    /// filter-effects-1 sets no upper bound. Values above 1 must pass through
    /// so an extended-range colour can carry them; clamping HERE would cap
    /// brightness(300) at brightness(100) and silently flatten the effect.
    func testAmountsAboveOneAreNotClamped() {
        XCTAssertEqual(FilterApplier.brightnessFactor(300), 3.0, accuracy: 1e-12)
    }

    /// Negative amounts are invalid CSS. Clamp to black rather than letting a
    /// negative multiplier invert the image, which is neither valid nor what
    /// any other runtime does.
    func testNegativeAmountsClampToBlackNotInverted() {
        XCTAssertEqual(FilterApplier.brightnessFactor(-50), 0.0, accuracy: 1e-12)
    }

    /// The measured fixture case, end to end in arithmetic: applying the
    /// factor to #2ecc71 must reproduce web's and Android's pixel.
    func testFixtureColourReproducesTheCrossPlatformResult() {
        let factor = FilterApplier.brightnessFactor(150)
        let source = (r: 46.0, g: 204.0, b: 113.0)          // #2ecc71
        let out = (r: min(255, (source.r * factor).rounded()),
                   g: min(255, (source.g * factor).rounded()),
                   b: min(255, (source.b * factor).rounded()))
        XCTAssertEqual(out.r, 69)
        XCTAssertEqual(out.g, 255, "204 x 1.5 = 306, clamped at the END per CSS")
        XCTAssertEqual(out.b, 170)
    }
}
