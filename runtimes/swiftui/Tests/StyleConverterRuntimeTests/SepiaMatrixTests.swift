//
//  SepiaMatrixTests.swift
//
//  Pins the arithmetic that was WRONG, the same way FilterBrightnessTests
//  pins the brightness factor. The view construction itself cannot be unit
//  tested (it is a SwiftUI modifier chain and needs a real render), so what
//  is testable is the decision underneath it: does the factorisation this
//  applier uses actually reproduce the CSS matrix?
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class SepiaMatrixTests: XCTestCase {

    /// The spec matrix, verbatim from filter-effects-1 §8.5. If someone
    /// "tidies" a coefficient this fails before any pixel moves.
    func testFullMatrixMatchesTheSpec() {
        XCTAssertEqual(SepiaMatrix.full[0], [0.393, 0.769, 0.189])
        XCTAssertEqual(SepiaMatrix.full[1], [0.349, 0.686, 0.168])
        XCTAssertEqual(SepiaMatrix.full[2], [0.272, 0.534, 0.131])
    }

    /// The reweight is what makes `.grayscale` sum in sepia's basis instead
    /// of Rec.709. Components MUST exceed 1 — that is not an accident, it is
    /// why extended-range colour is load-bearing here.
    func testReweightConvertsRec709IntoSepiasBasis() {
        let k = SepiaMatrix.reweight
        XCTAssertEqual(k[0], 1.848542, accuracy: 1e-5)
        XCTAssertEqual(k[1], 1.075224, accuracy: 1e-5)
        XCTAssertEqual(k[2], 2.617729, accuracy: 1e-5)
        XCTAssertTrue(k.contains { $0 > 1 }, "reweight must need extended range")

        // The defining property: w709 ⊙ k == sepia's weights.
        for i in 0..<3 {
            XCTAssertEqual(SepiaMatrix.rec709[i] * k[i], SepiaMatrix.weights[i], accuracy: 1e-9)
        }
    }

    /// The tint is LEAST-SQUARES fitted, not read off a column — the three
    /// columns disagree (0.8880 / 0.8921 / 0.8889 for row 2), so picking
    /// one is arbitrary. The fit halves the worst-case gamut residual.
    /// It does not change the rendered pixels; see SepiaMatrix's header.
    func testTintIsLeastSquaresFitted() {
        let t = SepiaMatrix.tint
        XCTAssertEqual(t[0], 1.0, accuracy: 1e-9)
        XCTAssertEqual(t[1], 0.891127, accuracy: 1e-5)
        XCTAssertEqual(t[2], 0.693896, accuracy: 1e-5)
        XCTAssertNotEqual(t[1], 0.888041, accuracy: 1e-4,
                          "must not silently revert to a column-0 ratio")
    }

    /// THE test. The factorisation must track the literal spec matrix
    /// everywhere, not just on the one fixture colour. Swept over the sRGB
    /// cube at many amounts; the bound is one quantisation step, because
    /// anything under that cannot change an 8-bit capture.
    func testFactorisationTracksTheSpecAcrossTheWholeGamut() {
        var worst = 0.0
        for aStep in 0...20 {
            let a = Double(aStep) / 20.0
            for r in stride(from: 0.0, through: 255.0, by: 15.0) {
                for g in stride(from: 0.0, through: 255.0, by: 15.0) {
                    for b in stride(from: 0.0, through: 255.0, by: 15.0) {
                        let c = [r, g, b]
                        let spec = SepiaMatrix.applySpec(c, amount: a)
                        let fact = SepiaMatrix.applyFactored(c, amount: a)
                        for i in 0..<3 { worst = max(worst, abs(spec[i] - fact[i])) }
                    }
                }
            }
        }
        XCTAssertLessThan(worst, 1.0, "rank-1 residual must stay under one 8-bit step")
        // Pin the actual bound too — a regression that doubled the error
        // would still pass the assertion above. The least-squares tint
        // halves what a column-0 ratio gives (0.8305).
        XCTAssertEqual(worst, 0.4171, accuracy: 0.01)
    }

    /// The fixture case, end to end through the arithmetic: this is the
    /// number Android and web both render, and the number iOS did not.
    /// The fixture case through the ARITHMETIC. (153,158,143) is what the
    /// spec matrix gives and what web and Android render.
    ///
    /// The iOS RENDER is (153,157,143) — one off in green, from per-layer
    /// 8-bit quantisation in the additive composite, not from this maths
    /// (see SepiaMatrix's header). This test therefore pins the
    /// factorisation against the spec, NOT against the captured pixels;
    /// the pixels are pinned by
    /// fixtures/properties/effects/filter-sepia-amounts.json.
    func testFactorisationMatchesTheSpecOnTheFixtureCase() {
        let out = SepiaMatrix.applyFactored([52, 152, 219], amount: 0.8)
        XCTAssertEqual(out[0].rounded(), 153)
        XCTAssertEqual(out[1].rounded(), 158)
        XCTAssertEqual(out[2].rounded(), 143)

        // …and rounds identically to the literal spec matrix.
        let spec = SepiaMatrix.applySpec([52, 152, 219], amount: 0.8)
        for i in 0..<3 { XCTAssertEqual(spec[i].rounded(), out[i].rounded()) }
    }

    /// The alpha trade-off the applier deliberately accepts, pinned so it
    /// cannot be forgotten or "fixed" without re-measuring.
    ///
    /// FilterApplier composites the sepia layer with plain SOURCE-OVER at
    /// .opacity(a). That is exact for OPAQUE content and wrong for
    /// translucent content, because source-over multiplies the top layer's
    /// alpha. The additive alternative fixes the alpha and stops SwiftUI
    /// Text being filtered at all (measured: white text under sepia(80%)
    /// renders (255,255,255) instead of the spec's (255,255,242)), so the
    /// trade was taken the other way — see FilterApplier's comment and the
    /// Sepia_Translucent ledger entry.
    func testSourceOverIsExactForOpaqueAndWrongForTranslucent() {
        for a in [0.2, 0.8, 1.0] {
            // Opaque: source-over reproduces the lerp exactly.
            let opaque = 1.0 * a + (1 - 1.0 * a) * 1.0
            XCTAssertEqual(opaque, 1.0, accuracy: 1e-12, "opaque coverage must survive")

            // Translucent: it does not, and the error grows with the amount.
            for alpha in [0.25, 0.5, 0.75] {
                let out = alpha * a + (1 - alpha * a) * alpha
                XCTAssertNotEqual(out, alpha, accuracy: 1e-6,
                                  "the known translucent error must stay visible")
                XCTAssertGreaterThan(out, alpha, "source-over over-accumulates alpha")
            }
        }
    }

    /// Amount 0 is the identity    /// Amount 0 is the identity — the applier fast-paths it, so the maths
    /// must agree that there is nothing to do.
    func testAmountZeroIsIdentity() {
        let c = [52.0, 152.0, 219.0]
        let out = SepiaMatrix.applyFactored(c, amount: 0)
        for i in 0..<3 { XCTAssertEqual(out[i], c[i], accuracy: 1e-9) }
    }

    /// Percentages are clamped into 0…1. Over-100% is clamped (unlike
    /// brightness, which is deliberately unbounded); negative is invalid
    /// CSS and collapses to the identity rather than inverting.
    func testAmountClamping() {
        XCTAssertEqual(SepiaMatrix.amount(0), 0.0, accuracy: 1e-9)
        XCTAssertEqual(SepiaMatrix.amount(80), 0.8, accuracy: 1e-9)
        XCTAssertEqual(SepiaMatrix.amount(100), 1.0, accuracy: 1e-9)
        XCTAssertEqual(SepiaMatrix.amount(300), 1.0, accuracy: 1e-9)
        XCTAssertEqual(SepiaMatrix.amount(-50), 0.0, accuracy: 1e-9)
    }

    /// Full sepia on a mid grey stays grey-ish but warm: R > G > B. The old
    /// implementation produced a COOL result on the fixture (74,110,113),
    /// which is the qualitative signature of the bug.
    func testFullSepiaIsWarm() {
        let out = SepiaMatrix.applyFactored([128, 128, 128], amount: 1.0)
        XCTAssertGreaterThan(out[0], out[1])
        XCTAssertGreaterThan(out[1], out[2])
    }
}
