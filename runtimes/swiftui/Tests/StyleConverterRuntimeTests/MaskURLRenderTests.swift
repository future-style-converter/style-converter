//
//  MaskURLRenderTests.swift
//  Wave-3 diagnostic pin for url() raster masks.
//
//  The url-mask path is the FIRST place a Canvas-based painter
//  (BackgroundURLImageView) runs INSIDE `.mask` under the harness's
//  ImageRenderer capture. The gradient mask layers are plain SwiftUI
//  gradient views and render fine there — this test isolates whether the
//  Canvas painter itself produces the expected alpha pattern when
//  rasterized by ImageRenderer, separating "the mask view is wrong" from
//  "the .mask composition is wrong" (the device capture showed a fully
//  UNMASKED box, i.e. an all-opaque mask).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class MaskURLRenderTests: XCTestCase {

    /// The wave-3 fixture's mask payload: 24x24 RGBA PNG, opaque black
    /// circle (r=10, centered) on a fully transparent ground. Rebuilt
    /// here minimally via UIGraphicsImageRenderer so the test does not
    /// depend on fixture files.
    private func circleMaskImage() -> UIImage {
        let r = UIGraphicsImageRenderer(size: CGSize(width: 24, height: 24))
        return r.image { ctx in
            UIColor.black.setFill()
            ctx.cgContext.fillEllipse(in: CGRect(x: 2, y: 2, width: 20, height: 20))
        }
    }

    /// Rasterize a view at 160x80@1x the way the harness captures.
    @MainActor
    private func render<V: View>(_ view: V) -> UIImage? {
        let renderer = ImageRenderer(content: view.frame(width: 160, height: 80))
        renderer.scale = 1
        return renderer.uiImage
    }

    private func alphaAt(_ img: UIImage, _ x: Int, _ y: Int) -> CGFloat {
        guard let cg = img.cgImage else { return -1 }
        guard let data = cg.dataProvider?.data as Data? else { return -1 }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        let alphaFirst = cg.alphaInfo == .premultipliedFirst || cg.alphaInfo == .first
        let idx = y * bpr + x * bpp + (alphaFirst ? 0 : 3)
        guard idx < data.count else { return -1 }
        return CGFloat(data[idx]) / 255
    }

    /// The Canvas painter itself, rendered stand-alone via ImageRenderer:
    /// tiles of the circle → alpha HIGH at a circle centre, LOW between
    /// tiles' corners. If this fails, the painter (not `.mask`) is broken
    /// under ImageRenderer.
    @MainActor
    func testCanvasPainterRendersAlphaPatternUnderImageRenderer() throws {
        let view = BackgroundURLImageView(uiImage: circleMaskImage(),
                                          sizeLayer: nil,
                                          positionX: nil,
                                          positionY: nil,
                                          repeatLayer: nil)
        let img = try XCTUnwrap(render(view), "ImageRenderer produced no image")
        // First tile's circle centre (12,12): opaque.
        XCTAssertGreaterThan(alphaAt(img, 12, 12), 0.9,
                             "circle centre should be opaque — Canvas painter drew nothing?")
        // Tile corner (0,0): transparent ground.
        XCTAssertLessThan(alphaAt(img, 0, 0), 0.1,
                          "tile corner should be transparent — painter filled opaquely?")
    }

    /// The FULL composition: a red rect masked by the url layer through
    /// MaskApplier — the alpha/color pattern of the result must show the
    /// circle grid (visible at circle centres, background elsewhere).
    @MainActor
    func testMaskCompositionClipsToCircleGrid() throws {
        var cfg = MaskConfig()
        cfg.touched = true
        // Feed the raster through the same enum the extractor emits. A
        // data URI exercises MaskURLLayer.resolve end-to-end; build one
        // from the in-test PNG so no fixture file dependency.
        let png = circleMaskImage().pngData()!
        let uri = "data:image/png," + png.map { String(format: "%%%02x", $0) }.joined()
        cfg.images = [.url(href: uri)]
        let base = Rectangle().fill(Color.red)
        let masked = base.modifier(MaskApplier(config: cfg))
        let img = try XCTUnwrap(render(masked), "ImageRenderer produced no image")
        XCTAssertGreaterThan(alphaAt(img, 12, 12), 0.9, "circle centre must keep the red fill")
        XCTAssertLessThan(alphaAt(img, 0, 0), 0.1,
                          "corner must be masked away — an all-opaque mask means url masks aren't masking")
    }
}
