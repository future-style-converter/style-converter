//
//  BackdropContractParityTests.swift
//  CROSS-NATIVE PIN TABLE for the unified wave-26 backdrop contract.
//
//  Every assertion in this file has a byte-identical twin in
//  runtimes/compose/src/test/java/com/styleconverter/runtime/effects/backdrop/
//  BackdropContractParityTest.kt. The two suites call each platform's own pure
//  functions with the SAME inputs and assert the SAME outputs, so "the natives
//  agree" is a diffable fact rather than a claim in a comment. Anything that
//  changes one table and not the other turns one of the two suites red.
//
//  The five clauses of the contract, and where each is pinned here:
//    1. blur edge model  — the sample table + keepRect table below;
//    2. chain admission  — the all-or-nothing table;
//    3. paint ordering   — pinned as a RASTER in BackdropTwoPassRasterTests
//                          (SwiftUI can be rendered off-device; Compose cannot,
//                          so its twin is a source-order pin);
//    4. clip box         — iOS attaches the margin modifier OUTSIDE the filter
//                          modifier (StyleBuilder), so the backplate's frame is
//                          already the border box; the pin is that ordering;
//    5. shadow leak      — same: `engineBoxShadow` is attached INSIDE
//                          `engineFilter`, so pass A's `.opacity(0)` already
//                          suppresses the element's own shadow.
//

import XCTest
// @testable: the geometry/plan types are module-internal.
@testable import StyleConverterRuntime

final class BackdropContractParityTests: XCTestCase {

    // MARK: - clause 1: the sample table (border box grown by 3σ, clamped)

    /// One row of the shared table. Field order matches the Kotlin twin's
    /// `Row` so the two tables can be read side by side.
    private struct Row {
        let name: String
        let elemLeft: Int, elemTop: Int, elemW: Int, elemH: Int
        let sigma: Double
        let srcW: Int, srcH: Int
        /// Expected sample, or nil for a refusal.
        let expect: BackdropSample?
        /// Expected keep rect (left, top, width, height) of the border box
        /// inside the crop, or nil when the row is a refusal.
        let keep: [Int]?
    }

    private let table: [Row] = [
        // Invert-only chain: σ=0 → pad 0 → the sample IS the border box, and
        // the keep rect is the whole crop. backdrop-filter-basic.html geometry.
        Row(name: "interior, no blur", elemLeft: 60, elemTop: 150, elemW: 100, elemH: 100,
            sigma: 0, srcW: 390, srcH: 600,
            expect: BackdropSample(srcLeft: 60, srcTop: 150, width: 100, height: 100,
                                   dstLeft: 0, dstTop: 0),
            keep: [0, 0, 100, 100]),
        // blur(10px) → pad 30 on every side, all of it available.
        Row(name: "interior, blur 10", elemLeft: 100, elemTop: 100, elemW: 50, elemH: 50,
            sigma: 10, srcW: 390, srcH: 600,
            expect: BackdropSample(srcLeft: 70, srcTop: 70, width: 110, height: 110,
                                   dstLeft: -30, dstTop: -30),
            keep: [30, 30, 50, 50]),
        // Box hugging the canvas origin: the padded rect would start at
        // (-30,-30). Clamped to the plate; edge duplication covers the rest.
        Row(name: "clamped at the canvas origin", elemLeft: 0, elemTop: 0, elemW: 50, elemH: 50,
            sigma: 10, srcW: 390, srcH: 600,
            expect: BackdropSample(srcLeft: 0, srcTop: 0, width: 80, height: 80,
                                   dstLeft: 0, dstTop: 0),
            keep: [0, 0, 50, 50]),
        // Same on the far edges.
        Row(name: "clamped at the far edges", elemLeft: 360, elemTop: 570, elemW: 30, elemH: 30,
            sigma: 10, srcW: 390, srcH: 600,
            expect: BackdropSample(srcLeft: 330, srcTop: 540, width: 60, height: 60,
                                   dstLeft: -30, dstTop: -30),
            keep: [30, 30, 30, 30]),
        // The element itself hangs off the LEFT edge: the crop is narrower
        // than the border box, and the keep rect says which strip it covers.
        Row(name: "element overhangs the left edge", elemLeft: -10, elemTop: 10,
            elemW: 40, elemH: 20, sigma: 0, srcW: 390, srcH: 600,
            expect: BackdropSample(srcLeft: 0, srcTop: 10, width: 30, height: 20,
                                   dstLeft: 10, dstTop: 0),
            keep: [0, 0, 30, 20]),
        // Refusals — nothing to filter / nothing to read / nothing on canvas.
        Row(name: "zero width refuses", elemLeft: 10, elemTop: 10, elemW: 0, elemH: 40,
            sigma: 0, srcW: 390, srcH: 600, expect: nil, keep: nil),
        Row(name: "zero height refuses", elemLeft: 10, elemTop: 10, elemW: 40, elemH: 0,
            sigma: 0, srcW: 390, srcH: 600, expect: nil, keep: nil),
        Row(name: "empty plate refuses", elemLeft: 10, elemTop: 10, elemW: 40, elemH: 40,
            sigma: 0, srcW: 0, srcH: 0, expect: nil, keep: nil),
        Row(name: "fully off canvas refuses", elemLeft: 500, elemTop: 10, elemW: 40, elemH: 40,
            sigma: 0, srcW: 390, srcH: 600, expect: nil, keep: nil),
    ]

    func testSampleTableMatchesTheCrossNativePins() {
        for row in table {
            let pad = BackdropSampleGeometry.blurPadPx(row.sigma)
            let got = BackdropSampleGeometry.sample(
                elemLeft: row.elemLeft, elemTop: row.elemTop,
                elemWidth: row.elemW, elemHeight: row.elemH,
                padPx: pad, srcWidth: row.srcW, srcHeight: row.srcH)
            XCTAssertEqual(got, row.expect, "sample[\(row.name)]")
        }
    }

    func testKeepRectTableMatchesTheCrossNativePins() throws {
        // The keep rect is the border box's own extent INSIDE the padded crop
        // — what the filtered buffer is cut back to. Compose expresses it
        // implicitly (the painter clips to the border box after drawing the
        // padded patch at a negative offset); its twin restates the same
        // closed form this function is.
        for row in table {
            guard let expect = row.keep else { continue }
            let sample = try XCTUnwrap(row.expect, "row \(row.name) has keep but no sample")
            let keep = try XCTUnwrap(BackdropSampleGeometry.keepRect(
                sample, elemWidth: row.elemW, elemHeight: row.elemH))
            XCTAssertEqual(keep.left, expect[0], "keep.left[\(row.name)]")
            XCTAssertEqual(keep.top, expect[1], "keep.top[\(row.name)]")
            XCTAssertEqual(keep.width, expect[2], "keep.width[\(row.name)]")
            XCTAssertEqual(keep.height, expect[3], "keep.height[\(row.name)]")
        }
    }

    func testBlurPadTableMatchesTheCrossNativePins() {
        // 3σ, rounded UP. NOT BackdropBlur.padding's 3σ+1 — that is the
        // separate replicate band applied inside the Gaussian, and it stays
        // one pixel wider to absorb CoreImage's σ→kernel rounding.
        XCTAssertEqual(BackdropSampleGeometry.blurPadPx(0), 0)
        XCTAssertEqual(BackdropSampleGeometry.blurPadPx(-3), 0)
        XCTAssertEqual(BackdropSampleGeometry.blurPadPx(10), 30)
        XCTAssertEqual(BackdropSampleGeometry.blurPadPx(2.5), 8)
        // Two 3px blurs compose in quadrature to σ = √18 ≈ 4.2426 → pad 13.
        let quadrature = BackdropPlan.plan(from: [.blur(radius: 3), .blur(radius: 3)])
            .totalBlurSigma
        XCTAssertEqual(quadrature, 4.2426, accuracy: 1e-3)
        XCTAssertEqual(BackdropSampleGeometry.blurPadPx(Double(quadrature)), 13)
    }

    // MARK: - clause 2: all-or-nothing admission

    func testAdmissionTableMatchesTheCrossNativePins() {
        // In scope, alone and combined, with CSS order preserved.
        XCTAssertEqual(BackdropPlan.plan(from: [.invert(pct: 100)]).ops,
                       [.invert(amount: 1.0)])
        XCTAssertEqual(BackdropPlan.plan(from: [.blur(radius: 10)]).ops,
                       [.blur(sigma: 10)])
        XCTAssertEqual(BackdropPlan.plan(from: [.blur(radius: 4), .invert(pct: 100)]).ops,
                       [.blur(sigma: 4), .invert(amount: 1.0)])
        // Out of scope → the WHOLE chain refuses. The second and third cases
        // are the ones that used to diverge: this planner executed the
        // recognised prefix and painted a half-filtered backdrop while Compose
        // painted none.
        XCTAssertTrue(BackdropPlan.plan(from: [.grayscale(pct: 100)]).isIdentity)
        XCTAssertTrue(BackdropPlan.plan(from: [.invert(pct: 100), .sepia(pct: 100)]).isIdentity)
        XCTAssertTrue(BackdropPlan.plan(from: [.blur(radius: 6),
                                               .brightness(pct: 110)]).isIdentity)
        // …and each of those is a REFUSAL, i.e. it gets logged.
        XCTAssertTrue(BackdropPlan.plan(from: [.grayscale(pct: 100)]).isRefused)
        // Identity chains are NOT refusals — they simply carry no work.
        XCTAssertTrue(BackdropPlan.plan(from: []).isIdentity)
        XCTAssertFalse(BackdropPlan.plan(from: []).isRefused)
    }

    // MARK: - clauses 4 + 5: the iOS chain positions those clauses rely on

    /// iOS needs no margin subtraction (clause 4) and leaks no shadow into
    /// pass A (clause 5) because of two StyleBuilder chain positions:
    /// `engineSpacingMargin` is attached AFTER `engineFilter` (so it WRAPS it
    /// — the backplate's GeometryReader frame is the border box), and
    /// `engineBoxShadow` is attached BEFORE it (so the shadow is inside the
    /// content `.opacity(0)` suppresses in pass A).
    ///
    /// Pinned at the source because both are statements about SwiftUI modifier
    /// ORDER in a 200-line builder expression, and a raster test would only
    /// catch them indirectly.
    func testStyleBuilderKeepsTheBackdropBetweenShadowAndMargin() throws {
        let src = try String(contentsOfFile: Self.repoPath(
            "runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/StyleBuilder.swift"),
            encoding: .utf8)
        let shadow = try XCTUnwrap(src.range(of: ".engineBoxShadow("))
        let filter = try XCTUnwrap(src.range(of: ".engineFilter("))
        let margin = try XCTUnwrap(src.range(of: ".engineSpacingMargin("))
        XCTAssertTrue(shadow.lowerBound < filter.lowerBound,
                      "box-shadow must be INSIDE the backdrop modifier, or pass A "
                      + "would bake the element's own shadow into its own backdrop")
        XCTAssertTrue(filter.lowerBound < margin.lowerBound,
                      "margin must WRAP the backdrop modifier, or the backplate's "
                      + "frame would be the margin box instead of the border box")
    }

    /// The backdrop modifier is attached AFTER the foreground filter loop, so
    /// the element's own `filter` chain cannot reach the backplate (clause 3).
    /// The rendered consequence is pinned in BackdropTwoPassRasterTests'
    /// double-apply test; this is the structural guard next to it.
    func testFilterApplierAttachesTheBackdropOutsideTheForegroundChain() throws {
        let src = try String(contentsOfFile: Self.repoPath(
            "runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/effects/filter/FilterApplier.swift"),
            encoding: .utf8)
        let body = try XCTUnwrap(src.range(of: "func body(content: Content)"))
        let tail = String(src[body.upperBound...])
        let loop = try XCTUnwrap(tail.range(of: "for fn in cfg.filter"))
        let attach = try XCTUnwrap(tail.range(of: "BackdropApplier(plan: plan"))
        XCTAssertTrue(loop.lowerBound < attach.lowerBound,
                      "the foreground chain must be folded BEFORE the backdrop "
                      + "modifier is attached, or it wraps the backplate")
    }

    /// Walk up from this source file until the relative path resolves — the
    /// test bundle's working directory is not the repo root.
    private static func repoPath(_ relative: String) -> String {
        var dir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        while dir.path != "/" {
            let candidate = dir.appendingPathComponent(relative)
            if FileManager.default.fileExists(atPath: candidate.path) { return candidate.path }
            dir = dir.deletingLastPathComponent()
        }
        return relative
    }
}
