//
//  BackdropMathTests.swift
//  Lane BF-I — the DEVICE-FREE half of the two-pass backdrop: the plan,
//  the crop, and the two image ops.
//
//  Everything here is pure CGImage/value math, so it runs in the SwiftPM
//  suite with no canvas and no simulator. The raster/view-tree half (pass
//  suppression, the backplate draw, the identity guarantee) is pinned
//  separately in BackdropTwoPassRasterTests.
//
//  The blur test is a CALIBRATION pin, not a smoke test: it recovers the
//  effective standard deviation from a blurred step edge and fails if
//  CIGaussianBlur's radius↔σ relation (BackdropBlur.ciRadiusPerSigma) or
//  the sRGB working-space pin ever drifts — both would silently change
//  every backdrop-blur capture.
//

import XCTest
import CoreGraphics
import SwiftUI
// @testable: the plan/ops/crop types are module-internal.
@testable import StyleConverterRuntime

final class BackdropMathTests: XCTestCase {

    // MARK: - image helpers

    /// sRGB, 8-bpc, RGBX image whose pixels come from `pixel(x, y)`.
    private func makeImage(_ w: Int, _ h: Int,
                           _ pixel: (Int, Int) -> (UInt8, UInt8, UInt8)) throws -> CGImage {
        let space = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        let ctx = try XCTUnwrap(CGContext(
            data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
            space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue))
        let base = try XCTUnwrap(ctx.data).bindMemory(
            to: UInt8.self, capacity: ctx.bytesPerRow * h)
        for y in 0..<h {
            for x in 0..<w {
                let (r, g, b) = pixel(x, y)
                let i = y * ctx.bytesPerRow + x * 4
                base[i] = r; base[i + 1] = g; base[i + 2] = b; base[i + 3] = 255
            }
        }
        return try XCTUnwrap(ctx.makeImage())
    }

    /// Read one pixel's RGB back, through a context with the SAME sRGB
    /// space so the probe never introduces a colour conversion of its own.
    private func rgb(_ image: CGImage, _ x: Int, _ y: Int) throws -> (Int, Int, Int) {
        let space = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        let ctx = try XCTUnwrap(CGContext(
            data: nil, width: image.width, height: image.height, bitsPerComponent: 8,
            bytesPerRow: 0, space: space,
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue))
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        let base = try XCTUnwrap(ctx.data).bindMemory(
            to: UInt8.self, capacity: ctx.bytesPerRow * image.height)
        let i = y * ctx.bytesPerRow + x * 4
        return (Int(base[i]), Int(base[i + 1]), Int(base[i + 2]))
    }

    // MARK: - 1. the plan (scope + disclosure)

    /// invert + blur are executed in declared order when the chain contains
    /// NOTHING else.
    func testPlanExecutesAnInScopeChainInDeclaredOrder() {
        let plan = BackdropPlan.plan(from: [.blur(radius: 6), .invert(pct: 100)])
        XCTAssertEqual(plan.ops, [.blur(sigma: 6), .invert(amount: 1.0)],
                       "executable ops keep CSS declaration order")
        XCTAssertTrue(plan.unsupported.isEmpty)
        XCTAssertFalse(plan.isRefused)
        XCTAssertFalse(plan.isIdentity)
    }

    /// ALL-OR-NOTHING (wave-26 skeptic fix #2): one out-of-scope function
    /// refuses the WHOLE chain. The recognised prefix is DISCARDED, not
    /// executed, so this planner admits exactly the set Compose's
    /// BackdropChain.of admits — the names survive for the refusal log.
    func testOutOfScopeFunctionRefusesTheWholeChain() {
        let plan = BackdropPlan.plan(from: [
            .blur(radius: 6), .brightness(pct: 110), .invert(pct: 100),
            .saturate(pct: 150), .dropShadow(x: 1, y: 1, blur: 2, color: nil),
        ])
        XCTAssertEqual(plan.ops, [], "a mixed chain must execute NO prefix")
        XCTAssertEqual(plan.unsupported, ["brightness", "saturate", "drop-shadow"])
        XCTAssertTrue(plan.isRefused)
        XCTAssertTrue(plan.isIdentity, "a refused chain draws no backplate at all")
        // The minimal case: one recognised op plus one that is not.
        let mixed = BackdropPlan.plan(from: [.invert(pct: 100), .sepia(pct: 100)])
        XCTAssertEqual(mixed.ops, [])
        XCTAssertEqual(mixed.unsupported, ["sepia"])
    }

    /// Identity arguments cost no pass: invert(0%) and blur(0) are exact
    /// no-ops, so they are neither ops nor refusals.
    func testIdentityArgumentsProduceAnIdentityPlan() {
        let plan = BackdropPlan.plan(from: [.invert(pct: 0), .blur(radius: 0)])
        XCTAssertTrue(plan.isIdentity)
        XCTAssertTrue(plan.unsupported.isEmpty)
        XCTAssertFalse(plan.isRefused, "an exact no-op is not a refusal")
        XCTAssertTrue(BackdropPlan.plan(from: []).isIdentity, "`none` → identity")
        XCTAssertFalse(BackdropPlan.plan(from: []).isRefused)
    }

    /// The chain's effective σ, which sizes the sample padding. Gaussians
    /// compose in quadrature — the same rule Compose's
    /// `BackdropChain.totalBlurSigmaPx` applies.
    func testTotalBlurSigmaComposesInQuadrature() {
        XCTAssertEqual(BackdropPlan.plan(from: [.blur(radius: 3), .blur(radius: 3)])
                        .totalBlurSigma, 4.2426, accuracy: 1e-3)
        XCTAssertEqual(BackdropPlan.plan(from: [.invert(pct: 100)]).totalBlurSigma, 0,
                       "an invert-only chain has no spread")
    }

    /// Percentages normalise to the spec's 0…1 amount and clamp there.
    func testInvertAmountNormalisesAndClamps() {
        XCTAssertEqual(BackdropPlan.plan(from: [.invert(pct: 50)]).ops,
                       [.invert(amount: 0.5)])
        XCTAssertEqual(BackdropPlan.plan(from: [.invert(pct: 300)]).ops,
                       [.invert(amount: 1.0)], "out-of-range amount clamps to 1")
    }

    // MARK: - 2. invert (filter-effects-1 §6.1)

    /// The byte formula, spelled out at the three amounts that matter.
    /// This is the arithmetic the Compose twin must reproduce channel for
    /// channel — the two natives are diffed pixel-for-pixel.
    func testInvertByteTruthTable() {
        // amount 1 → 255 − c.
        XCTAssertEqual(BackdropImageOps.invertByte(0, amount: 1), 255)
        XCTAssertEqual(BackdropImageOps.invertByte(255, amount: 1), 0)
        XCTAssertEqual(BackdropImageOps.invertByte(200, amount: 1), 55)
        // amount 0 → identity.
        XCTAssertEqual(BackdropImageOps.invertByte(0, amount: 0), 0)
        XCTAssertEqual(BackdropImageOps.invertByte(200, amount: 0), 200)
        // amount 0.5 → every channel collapses to the 127.5 midpoint,
        // rounded half-away-from-zero to 128.
        for c: UInt8 in [0, 100, 200, 255] {
            XCTAssertEqual(BackdropImageOps.invertByte(c, amount: 0.5), 128)
        }
    }

    /// Whole-image invert hits all three channels and leaves the image's
    /// dimensions alone.
    func testInvertImageInvertsEveryChannel() throws {
        let src = try makeImage(4, 3) { _, _ in (30, 100, 200) }
        let out = try XCTUnwrap(BackdropImageOps.invert(src, amount: 1.0))
        XCTAssertEqual(out.width, 4)
        XCTAssertEqual(out.height, 3)
        let (r, g, b) = try rgb(out, 2, 1)
        XCTAssertEqual(r, 225); XCTAssertEqual(g, 155); XCTAssertEqual(b, 55)
    }

    // MARK: - 3. blur

    /// CALIBRATION: blur a black→white step by σ = 4 px and recover σ from
    /// the profile. For a step convolved with a Gaussian,
    ///   Σ_{x ≥ edge} (255 − v(x)) / 255  ≈  σ / √(2π)  =  0.39894 σ
    /// (the midpoint-rule form of ∫(1−Φ)). The estimate therefore checks
    /// BOTH that CIGaussianBlur's radius is being fed as a σ and that the
    /// blur runs in the sRGB working space — a linear-space blur would
    /// lift the profile and inflate the estimate.
    func testBlurRecoversDeclaredSigmaFromAStepEdge() throws {
        let w = 64, h = 8, edge = 32
        let step = try makeImage(w, h) { x, _ in
            x < edge ? (0, 0, 0) : (255, 255, 255)
        }
        let blurred = try XCTUnwrap(BackdropBlur.blur(step, sigmaPixels: 4))
        XCTAssertEqual(blurred.width, w, "blur preserves pixel dimensions")
        XCTAssertEqual(blurred.height, h)
        // Tail integral over the bright side of the middle row.
        var tail = 0.0
        for x in edge..<w { tail += Double(255 - (try rgb(blurred, x, h / 2)).0) / 255.0 }
        let sigma = tail / 0.39894
        XCTAssertEqual(sigma, 4.0, accuracy: 0.5,
                       "recovered σ \(sigma) — CIGaussianBlur radius↔σ or the "
                       + "sRGB working space drifted")
        // And the edge itself sits at the 50% crossing.
        XCTAssertEqual((try rgb(blurred, edge, h / 2)).0, 128, accuracy: 12)
    }

    /// EDGE EXTENSION: a uniform crop must survive a blur unchanged. With
    /// CoreImage's default transparent-black surround the border pixels
    /// would darken — the halo the browser never paints. (Uniform input is
    /// the one case where mirror and replicate agree, so this test survives
    /// the wave-34 model change unchanged; the two are separated by
    /// testTheBlurCannotReachOutsideTheBorderBox and the corpus.)
    func testBlurKeepsUniformCropUniformAtTheBorder() throws {
        let src = try makeImage(24, 24) { _, _ in (200, 60, 30) }
        let out = try XCTUnwrap(BackdropBlur.blur(src, sigmaPixels: 5))
        // Corner, edge midpoint, and centre — the three places a missing
        // pad shows up first.
        for (x, y) in [(0, 0), (23, 23), (0, 12), (12, 0), (12, 12)] {
            let (r, g, b) = try rgb(out, x, y)
            XCTAssertEqual(r, 200, accuracy: 3, "faded at (\(x),\(y))")
            XCTAssertEqual(g, 60, accuracy: 3)
            XCTAssertEqual(b, 30, accuracy: 3)
        }
    }

    /// The pad width is the MEASURED kernel support — 3σ + 1 px — not the
    /// 2σ "visually significant tail" (which measurably leaked; see
    /// BackdropBlur.padding and the border test above).
    func testPaddingCoversTheMeasuredKernelSupport() {
        XCTAssertEqual(BackdropBlur.padding(forSigmaPixels: 4), 13)
        XCTAssertEqual(BackdropBlur.padding(forSigmaPixels: 3.2), 11)
        XCTAssertEqual(BackdropBlur.padding(forSigmaPixels: 0.1), 2)
    }

    // MARK: - 4. crop (points → plate pixels)

    /// A fully-inside box crops to its own rect, with a zero local origin and
    /// a keep rect covering the whole crop.
    func testCropInsidePlate() throws {
        let plate = BackdropPlate(image: try makeImage(100, 50) { _, _ in (1, 2, 3) },
                                  scale: 1)
        let crop = try XCTUnwrap(BackdropImageOps.crop(
            plate, elementFrame: CGRect(x: 10, y: 5, width: 30, height: 20)))
        XCTAssertEqual(crop.image.width, 30)
        XCTAssertEqual(crop.image.height, 20)
        XCTAssertEqual(crop.keepRect, CGRect(x: 0, y: 0, width: 30, height: 20))
        XCTAssertEqual(crop.localRect, CGRect(x: 0, y: 0, width: 30, height: 20))
    }

    /// BORDER-BOX crop (wave-34 lane B): the sample is the element's own box,
    /// with no σ term, and `keepRect` is therefore the whole crop.
    ///
    /// filter-effects-2 §2 clips the backdrop to the border box BEFORE the
    /// filter runs; the mirror band inside `BackdropBlur` supplies whatever
    /// the Gaussian wants beyond it. See BackdropSampleGeometry's header for
    /// the per-tile measurement against the Chrome ref that refuted the
    /// wave-26 grow-by-3σ crop.
    func testCropIsTheBorderBoxRegardlessOfBlur() throws {
        let plate = BackdropPlate(image: try makeImage(200, 200) { _, _ in (1, 2, 3) },
                                  scale: 1)
        let crop = try XCTUnwrap(BackdropImageOps.crop(
            plate, elementFrame: CGRect(x: 100, y: 100, width: 50, height: 50)))
        XCTAssertEqual(crop.image.width, 50, "the border box, not 50 + 2×3σ")
        XCTAssertEqual(crop.image.height, 50)
        XCTAssertEqual(crop.keepRect, CGRect(x: 0, y: 0, width: 50, height: 50))
        // The kept pixels still land exactly on the element's own box.
        XCTAssertEqual(crop.localRect, CGRect(x: 0, y: 0, width: 50, height: 50))
    }

    /// A box at the plate's own boundary needs no clamp any more — the sample
    /// never reaches outside the box, so the origin case is the ordinary one.
    func testCropAtThePlateBoundaryNeedsNoClamp() throws {
        let plate = BackdropPlate(image: try makeImage(200, 200) { _, _ in (1, 2, 3) },
                                  scale: 1)
        let crop = try XCTUnwrap(BackdropImageOps.crop(
            plate, elementFrame: CGRect(x: 0, y: 0, width: 50, height: 50)))
        XCTAssertEqual(crop.image.width, 50)
        XCTAssertEqual(crop.keepRect, CGRect(x: 0, y: 0, width: 50, height: 50))
        XCTAssertEqual(crop.localRect, CGRect(x: 0, y: 0, width: 50, height: 50))
    }

    /// An overhanging box is CLAMPED, and the local rect says which part of
    /// the border box the (smaller) crop actually covers — so the applier
    /// leaves the uncovered strip unpainted instead of stretching pixels.
    func testCropClampsOverhangAndReportsTheCoveredRect() throws {
        let plate = BackdropPlate(image: try makeImage(100, 50) { _, _ in (1, 2, 3) },
                                  scale: 1)
        let crop = try XCTUnwrap(BackdropImageOps.crop(
            plate, elementFrame: CGRect(x: -10, y: 10, width: 40, height: 20)))
        XCTAssertEqual(crop.image.width, 30, "10pt of the box is off-canvas")
        XCTAssertEqual(crop.keepRect, CGRect(x: 0, y: 0, width: 30, height: 20))
        XCTAssertEqual(crop.localRect, CGRect(x: 10, y: 0, width: 30, height: 20))
        // Entirely off-canvas → no crop at all (applier paints nothing).
        XCTAssertNil(BackdropImageOps.crop(
            plate, elementFrame: CGRect(x: 200, y: 0, width: 10, height: 10)))
    }

    /// A 2× plate crops twice as many pixels for the same point rect, and
    /// still reports the rect in POINTS.
    func testCropHonoursPlateScale() throws {
        let plate = BackdropPlate(image: try makeImage(200, 100) { _, _ in (9, 9, 9) },
                                  scale: 2)
        let crop = try XCTUnwrap(BackdropImageOps.crop(
            plate, elementFrame: CGRect(x: 10, y: 10, width: 20, height: 20)))
        XCTAssertEqual(crop.image.width, 40)
        XCTAssertEqual(crop.keepRect, CGRect(x: 0, y: 0, width: 40, height: 40))
        XCTAssertEqual(crop.localRect, CGRect(x: 0, y: 0, width: 20, height: 20))
    }

    // MARK: - 4b. filter-then-cut-back, over a plate with real structure

    /// THE WAVE-34 FIX, measured. A plate split black|green at x = 100; a
    /// 40×40 box whose LEFT edge sits flush on the split, blurred by σ = 8.
    ///
    /// Wave 26 grew the sample 3σ to the left so the Gaussian mixed the BLACK
    /// outside the box in, pulling the box's leftmost column down to ≈(0,117,29).
    /// filter-effects-2 §2 says the opposite: the backdrop is CLIPPED TO THE
    /// BORDER BOX before the filter runs, so black on the other side of the
    /// box's edge is not an input at all and the column stays green.
    ///
    /// The corpus is the arbiter, not this synthetic plate:
    /// css/filter-effects/backdrop-filter-boundary.html asserts in so many
    /// words that "No lime green should be brought in to the blurred regions",
    /// and under the grown sample both natives imported exactly that lime —
    /// error growing with σ, 1.6 → 99.2 mean absolute error per tile against
    /// the Chrome 151 ref, while Chrome's own capture tracked the ref to 0.0.
    func testTheBlurCannotReachOutsideTheBorderBox() throws {
        let plate = BackdropPlate(
            image: try makeImage(200, 80) { x, _ in x < 100 ? (0, 0, 0) : (0, 204, 51) },
            scale: 1)
        let box = CGRect(x: 100, y: 20, width: 40, height: 40)
        let sigma: CGFloat = 8
        let crop = try XCTUnwrap(BackdropImageOps.crop(plate, elementFrame: box))
        XCTAssertEqual(crop.image.width, 40, "the sample is the border box")
        let out = try XCTUnwrap(BackdropImageOps.filtered(
            crop, ops: [.blur(sigma: sigma)], scale: 1))
        XCTAssertEqual(out.width, 40)
        XCTAssertEqual(out.height, 40)
        // Leftmost column: untouched green. A value near 102 means the
        // wave-26 grown sample is back and the black outside is bleeding in.
        let edge = try rgb(out, 0, 20)
        XCTAssertEqual(edge.1, 204, accuracy: 6,
                       "green at the box's left edge must survive — got \(edge.1); "
                       + "a value near 102 means the grown-sample model is back")
        // Deep inside the box the green is intact too (uniform crop in, out).
        let inside = try rgb(out, 39, 20)
        XCTAssertEqual(inside.1, 204, accuracy: 4)
    }

    /// The mirror band is what makes the box's own edge survive the Gaussian:
    /// with CoreImage's default transparent-black surround, a uniform crop
    /// would fade at its border. Same plate, box fully inside the green half.
    func testTheMirrorBandKeepsAUniformCropUniform() throws {
        let plate = BackdropPlate(
            image: try makeImage(200, 80) { x, _ in x < 100 ? (0, 0, 0) : (0, 204, 51) },
            scale: 1)
        let crop = try XCTUnwrap(BackdropImageOps.crop(
            plate, elementFrame: CGRect(x: 140, y: 20, width: 40, height: 40)))
        let out = try XCTUnwrap(BackdropImageOps.filtered(
            crop, ops: [.blur(sigma: 8)], scale: 1))
        for (x, y) in [(0, 0), (39, 0), (0, 39), (39, 39), (20, 20)] {
            let px = try rgb(out, x, y)
            XCTAssertEqual(px.1, 204, accuracy: 4, "green at (\(x),\(y))")
        }
    }

    /// The mirror grid's two pure rules, pinned without a raster: how many
    /// reflected copies a band needs, and which of them are flipped.
    func testMirrorPadGridRules() {
        // A band narrower than the tile still needs one reflection each side.
        XCTAssertEqual(BackdropBlur.mirrorCopies(pad: 10, extent: 40), 1)
        XCTAssertEqual(BackdropBlur.mirrorCopies(pad: 40, extent: 40), 1)
        // Wider than the tile → integer-ceiling many copies (the WPT
        // reference file's copiesX/copiesY).
        XCTAssertEqual(BackdropBlur.mirrorCopies(pad: 41, extent: 40), 2)
        XCTAssertEqual(BackdropBlur.mirrorCopies(pad: 289, extent: 150), 2)
        // Degenerate inputs ask for nothing.
        XCTAssertEqual(BackdropBlur.mirrorCopies(pad: 0, extent: 40), 0)
        XCTAssertEqual(BackdropBlur.mirrorCopies(pad: 10, extent: 0), 0)
        // Parity: the centre tile is upright, every odd step out is flipped.
        XCTAssertFalse(BackdropBlur.mirrorFlips(0))
        XCTAssertTrue(BackdropBlur.mirrorFlips(1))
        XCTAssertTrue(BackdropBlur.mirrorFlips(-1))
        XCTAssertFalse(BackdropBlur.mirrorFlips(2))
        XCTAssertFalse(BackdropBlur.mirrorFlips(-2))
    }

    /// `filtered` is size-preserving for an on-canvas box: the crop IS the
    /// border box, so the cut-back is the identity.
    func testFilteredKeepsTheBorderBoxSize() throws {
        let plate = BackdropPlate(image: try makeImage(60, 60) { _, _ in (30, 100, 200) },
                                  scale: 1)
        let crop = try XCTUnwrap(BackdropImageOps.crop(
            plate, elementFrame: CGRect(x: 10, y: 10, width: 20, height: 20)))
        let out = try XCTUnwrap(BackdropImageOps.filtered(
            crop, ops: [.invert(amount: 1)], scale: 1))
        XCTAssertEqual(out.width, 20)
        XCTAssertEqual(out.height, 20)
        let (r, g, b) = try rgb(out, 10, 10)
        XCTAssertEqual(r, 225); XCTAssertEqual(g, 155); XCTAssertEqual(b, 55)
    }

    // MARK: - 5. the two-pass predicate

    /// `needsTwoPass` finds a BackdropFilter at any depth and stays false
    /// for a document without one — the guarantee that a backdrop-free
    /// capture still runs exactly one, unchanged, render pass.
    func testNeedsTwoPassFindsNestedDeclarations() throws {
        func doc(_ json: String) throws -> [IRComponent] {
            [try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))]
        }
        let plain = try doc("""
        {"id":"a","name":"A","properties":[{"type":"Width","data":{"px":10}}]}
        """)
        XCTAssertFalse(BackdropCapture.needsTwoPass(plain))
        let nested = try doc("""
        {"id":"a","name":"A","properties":[],"children":[
          {"id":"b","name":"B","properties":[
            {"type":"BackdropFilter","data":[{"fn":"invert","v":100}]}]}]}
        """)
        XCTAssertTrue(BackdropCapture.needsTwoPass(nested))
        XCTAssertFalse(BackdropCapture.needsTwoPass([]), "empty doc → one pass")
    }
}
