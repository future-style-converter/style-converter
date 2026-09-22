//
//  CaptureCanvasMirror.swift
//  Wave 51 PR (A) — the shared byte-mirror of the harness CaptureCanvas.
//
//  Hoisted out of BlendGroupCompositingTests (where `raster`, `flowCanvas`,
//  `outOfFlowCanvas` and `count` were private) so HarnessLabelChromeRasterTests
//  can compose onto the SAME chain. The view-returning builders below are
//  the byte-mirror of apps/ios-harness/…/Screenshot/CaptureCanvas.swift —
//  proven byte-IDENTICAL to the simulator captures they predict (wave 50:
//  5/5 opacity-blend crops and both committed visual-test blend baselines
//  diffed at 0 px). Every modifier below is listed in the order the harness
//  applies it; keep the two in lock-step when CaptureCanvas.swift moves.
//
//  `withLabelChrome` (default false, so BlendGroupCompositingTests' chains
//  stay byte-identical to what they were) inserts the PR (A) label overlay
//  at exactly the position CaptureCanvas.swift mounts it — right after
//  `.background(…)` and before the environment nodes — so the chrome test
//  measures the harness's real mounting point, not a convenient one.
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

/// RGBA8 buffer + dimensions, as every raster helper returns it.
typealias RasterImage = (px: [UInt8], w: Int, h: Int)

/// Namespace for the mirror (a caseless enum — no instances, only statics).
enum CaptureCanvasMirror {

    /// apps/ios-harness `CaptureCanvas.backgroundColor` — the #1A1A2E stage
    /// every committed baseline was captured on.
    static let ground = Color(red: 0x1A / 255.0, green: 0x1A / 255.0, blue: 0x2E / 255.0)

    /// The harness's published capture geometry (390×844, 358 root CB).
    static let viewport = StyleViewport(width: 390, height: 844, rootContainingBlock: 358)

    /// The harness's default frame width (CaptureOverrides.captureWidth
    /// with CAPTURE_WIDTH unset) — the number the label truncates against.
    static let frameWidth: CGFloat = 390

    /// CaptureCanvas's in-flow branch: 390 wide, 16pt pad, natural height,
    /// then the stage background, then (PR (A)) the label chrome as a
    /// sibling overlay, then the environment nodes.
    @MainActor
    static func flowCanvasView(_ c: IRComponent,
                               withLabelChrome: Bool = false) -> some View {
        ComponentHost(component: c)
            .frame(maxWidth: 390 - 32, alignment: RootAlignment.alignment(for: c))
            .padding(16)
            .frame(width: 390, alignment: .topLeading)
            .fixedSize(horizontal: false, vertical: true)
            .background(ground)
            // CaptureCanvas.swift in-flow branch: `.overlay(alignment:
            // .topLeading) { HarnessLabelChrome(…) }` right after
            // `.background(canvasBackground)`. An EmptyView overlay is
            // paint- and layout-neutral, so the `false` arm changes nothing.
            .overlay(alignment: .topLeading) {
                if withLabelChrome {
                    HarnessLabelChrome(component: c, frameWidth: frameWidth)
                }
            }
            .environment(\.styleViewport, viewport)
    }

    /// CaptureCanvas's out-of-flow branch: the collapsed 390×32 card an
    /// abspos capture subject gets, anchored at the card corner because both
    /// axes declare an inset (CSS 2.1 §10.3.7 / §10.6.4); `.clipped()` then
    /// the stage, then (PR (A)) the chrome OUTSIDE that clip.
    @MainActor
    static func outOfFlowCanvasView(_ c: IRComponent,
                                    withLabelChrome: Bool = false) -> some View {
        Color.clear
            .frame(width: 390, height: 32)
            .overlay(alignment: .topLeading) { ComponentHost(component: c) }
            .clipped()
            .background(ground)
            // CaptureCanvas.swift out-of-flow branch: the chrome mounts after
            // `.clipped()` + `.background(…)`, so the card clip cannot cut it.
            .overlay(alignment: .topLeading) {
                if withLabelChrome {
                    HarnessLabelChrome(component: c, frameWidth: frameWidth)
                }
            }
            .environment(\.styleViewport, viewport)
    }

    /// Rasterise at scale 1 and read the bytes back through a CGContext
    /// redraw, because ImageRenderer's native buffer layout is not
    /// guaranteed (the Backface3DSubtreeRasterTests pattern).
    @MainActor
    static func raster<V: View>(_ view: V) throws -> RasterImage {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (buf, w, h)
    }

    /// Convenience: raster of the in-flow mirror (the pre-(A) `flowCanvas`).
    @MainActor
    static func flowCanvas(_ c: IRComponent) throws -> RasterImage {
        try raster(flowCanvasView(c))
    }

    /// Convenience: raster of the out-of-flow mirror (`outOfFlowCanvas`).
    @MainActor
    static func outOfFlowCanvas(_ c: IRComponent) throws -> RasterImage {
        try raster(outOfFlowCanvasView(c))
    }

    /// Pixels within `tol` per channel of `rgb`, over the whole image.
    static func count(_ img: RasterImage, _ rgb: (Int, Int, Int), tol: Int) -> Int {
        var n = 0
        for i in stride(from: 0, to: img.w * img.h * 4, by: 4)
        where abs(Int(img.px[i]) - rgb.0) <= tol
            && abs(Int(img.px[i + 1]) - rgb.1) <= tol
            && abs(Int(img.px[i + 2]) - rgb.2) <= tol { n += 1 }
        return n
    }

    /// RGB triple at (x, y) — out-of-range reads return (-1,-1,-1) so a
    /// probe past the raster fails loudly in the assertion, never traps.
    static func rgb(_ img: RasterImage, _ x: Int, _ y: Int) -> (Int, Int, Int) {
        guard x >= 0, y >= 0, x < img.w, y < img.h else { return (-1, -1, -1) }
        let i = (y * img.w + x) * 4
        return (Int(img.px[i]), Int(img.px[i + 1]), Int(img.px[i + 2]))
    }
}
