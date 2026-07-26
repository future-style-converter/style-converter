//
//  SkepticRtlGridPlacementTests.swift
//  Wave-19 grid-distribution SKEPTIC regression — pins the interaction the
//  pure trackOrigins pins cannot see: SwiftUI's Layout engine MIRRORS every
//  place(at:) x-coordinate about the layout bounds (x' = W − x − w) when
//  the ambient layoutDirection environment is RTL. TypographyApplier sets
//  that environment from the same Direction wire the renderer reads, so
//  CSSGridLayout must solve in LOGICAL space and let the single ambient
//  mirror produce the physical css-grid-1 §7.1 RTL geometry. The original
//  wave-19 build pre-mirrored the origins inside solve() as well — this
//  ImageRenderer pixel probe caught the resulting double flip (an RTL
//  Direction grid rendered its track back at the LTR position).
//
//  Geometry is the descendant-static-position-003/002 family shape: one
//  fixed 40px column inside a definite 100px grid.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class SkepticRtlGridPlacementTests: XCTestCase {

    /// Render a 100x40 definite single-column (40px) CSSGridLayout holding
    /// one red 40x40 item, optionally under the RTL layout-direction
    /// environment (the exact environment TypographyApplier's
    /// LayoutDirectionMod sets for Direction:RTL), and report whether the
    /// left half (x≈20) / right half (x≈80) midline pixels are red.
    @MainActor
    private func renderProbe(justify: AlignmentKeyword?,
                             rtlEnvironment: Bool) -> (leftRed: Bool, rightRed: Bool)? {
        let grid = CSSGridLayout(
            tracks: [.fixed(px: 40)],
            rowTemplate: [.fixed(px: 40)],
            autoRows: nil,
            justifyItems: .start,
            alignItems: .start,
            rowGap: 0,
            columnGap: 0,
            definiteWidth: true,
            templateAreas: nil,
            justifyContent: justify
        ) {
            Color.red.frame(width: 40, height: 40)
        }
        let view = grid
            .frame(width: 100, height: 40, alignment: .topLeading)
            .background(Color.white)
        let renderer: ImageRenderer<AnyView> = rtlEnvironment
            ? ImageRenderer(content: AnyView(view.environment(\.layoutDirection, .rightToLeft)))
            : ImageRenderer(content: AnyView(view))
        renderer.scale = 1
        guard let cg = renderer.cgImage,
              let data = cg.dataProvider?.data,
              let ptr = CFDataGetBytePtr(data) else { return nil }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        func redAt(_ x: Int, _ y: Int) -> Bool {
            let p = y * bpr + x * bpp
            // Channel-order agnostic: a pure red pixel has exactly one
            // saturated channel among the first three; white has three.
            let highs = [ptr[p], ptr[p + 1], ptr[p + 2]].filter { $0 > 200 }.count
            return highs == 1
        }
        return (leftRed: redAt(20 * cg.width / 100, cg.height / 2),
                rightRed: redAt(80 * cg.width / 100, cg.height / 2))
    }

    /// LTR control: justify-content default (start) keeps the track at the
    /// physical left — proves the probe itself has no lateral bias.
    @MainActor
    func testLtrStartPaintsTrackOnLeft() throws {
        guard let r = renderProbe(justify: nil, rtlEnvironment: false) else {
            throw XCTSkip("ImageRenderer produced no image in this environment")
        }
        XCTAssertTrue(r.leftRed, "LTR start: track must paint on the left")
        XCTAssertFalse(r.rightRed, "LTR start: right half must stay white")
    }

    /// LTR justify-content:end — the wave-19 distribution shift (003 pin:
    /// 40px track at x=60 inside 100px).
    @MainActor
    func testLtrEndPaintsTrackOnRight() throws {
        guard let r = renderProbe(justify: .end, rtlEnvironment: false) else {
            throw XCTSkip("ImageRenderer produced no image in this environment")
        }
        XCTAssertFalse(r.leftRed, "LTR end: left half must stay white")
        XCTAssertTrue(r.rightRed, "LTR end: track must paint at x=60..100")
    }

    /// THE double-flip pin: under the RTL environment (the real render path
    /// for a Direction:RTL grid) justify-content default start = inline
    /// start = the RIGHT physical edge. Exactly ONE mirror — SwiftUI's —
    /// may apply; a second (pre-mirrored origins inside solve) would land
    /// the track back on the left, the pre-wave-19 broken geometry.
    @MainActor
    func testRtlEnvironmentStartPaintsTrackOnRightNotDoubleFlipped() throws {
        guard let r = renderProbe(justify: nil, rtlEnvironment: true) else {
            throw XCTSkip("ImageRenderer produced no image in this environment")
        }
        XCTAssertFalse(r.leftRed, "RTL start: left half must stay white (double flip = red here)")
        XCTAssertTrue(r.rightRed, "RTL start: track must pack to the physical right edge")
    }

    /// RTL + justify-content:end — inline end = the LEFT physical edge:
    /// logical lead 60 mirrored once → x=0 (descendant-static-position-004
    /// family symmetry check).
    @MainActor
    func testRtlEnvironmentEndPaintsTrackOnLeft() throws {
        guard let r = renderProbe(justify: .end, rtlEnvironment: true) else {
            throw XCTSkip("ImageRenderer produced no image in this environment")
        }
        XCTAssertTrue(r.leftRed, "RTL end: track must pack to the physical left edge")
        XCTAssertFalse(r.rightRed, "RTL end: right half must stay white")
    }
}
