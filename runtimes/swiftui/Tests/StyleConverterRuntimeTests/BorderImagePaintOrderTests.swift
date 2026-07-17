//
//  BorderImagePaintOrderTests.swift
//  IOS-BI lane — paint-order pin for the border-image nine-slice.
//
//  CSS paints border-image above the background chain but BENEATH the
//  element's content (css-backgrounds-3 §6 draws it "in place of the
//  border"; CSS2 Appendix E paints borders after backgrounds, before
//  content). The applier used to attach its Canvas as an `.overlay`,
//  which inverted that: with slice `fill`, the center rectangle painted
//  ABOVE the content box and hid the label web/Android correctly keep
//  on top. These pins rasterize the modifier via ImageRenderer — the
//  same capture path the harness uses — and assert the two halves of
//  the fix: (1) content stays visible above a slice-`fill` center, and
//  (2) the §6.4 outset paints OUTSIDE the host's bounds (this pin
//  surfaced a pre-existing GraphicsContext culling of out-of-bounds
//  image draws — see testOutsetStillPaintsOutsideHostBoundsAsBackground).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class BorderImagePaintOrderTests: XCTestCase {

    // MARK: - fixtures

    /// 30×30 solid RED source raster. A uniform color means every slice
    /// region (corners, edges, and the §6.1 `fill` center) paints pure
    /// red, so any red sample proves "border image painted here" and
    /// any non-red sample proves "something else is on top / nothing".
    private func redSourceImage() -> UIImage {
        let r = UIGraphicsImageRenderer(size: CGSize(width: 30, height: 30))
        return r.image { ctx in
            UIColor.red.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 30, height: 30))
        }
    }

    /// Percent-encoded RFC 2397 data URI for the red raster — the exact
    /// wire shape the resolver documents as safe (the converter
    /// lowercases url() payloads, which corrupts base64, so the runtime
    /// tests standardize on %xx encoding like MaskURLRenderTests).
    private func redDataURI() -> String {
        let png = redSourceImage().pngData()!
        return "data:image/png," + png.map { String(format: "%%%02x", $0) }.joined()
    }

    /// A config exercising the diagnosed bug: url() source, 10px slices
    /// with `fill` (so the CENTER paints), 8px literal widths (so
    /// canPaint passes without any computed border), plus a caller-set
    /// outset for the outside-the-bounds pin.
    private func fillConfig(outset: CGFloat = 0) -> BorderImageConfig {
        var cfg = BorderImageConfig()
        // The decodable in-test source — no fixture-file dependency.
        cfg.source = .url(redDataURI())
        // §6.1 absolute-pixel slice lines, 10px on every edge.
        let s = BorderImageSliceEdge(value: 10, isPercent: false)
        cfg.sliceTop = s; cfg.sliceRight = s; cfg.sliceBottom = s; cfg.sliceLeft = s
        // The bug's trigger: `fill` paints the center over the content box.
        cfg.sliceFill = true
        // §6.3 literal lengths — independent of computed border widths.
        let w = BorderImageDimension.length(8)
        cfg.widthTop = w; cfg.widthRight = w; cfg.widthBottom = w; cfg.widthLeft = w
        // §6.4 literal outset when the pin needs paint outside the box.
        if outset > 0 {
            let o = BorderImageDimension.length(outset)
            cfg.outsetTop = o; cfg.outsetRight = o
            cfg.outsetBottom = o; cfg.outsetLeft = o
        }
        return cfg
    }

    // MARK: - raster plumbing (mirrors MaskURLRenderTests)

    /// Rasterize at @1x the way the harness captures.
    @MainActor
    private func render<V: View>(_ view: V, size: CGSize) -> UIImage? {
        let renderer = ImageRenderer(content: view.frame(width: size.width, height: size.height))
        renderer.scale = 1
        return renderer.uiImage
    }

    /// RGBA at a pixel, normalized 0–1. ImageRenderer's backing CGImage
    /// varies its byte order across OS versions, so decode the layout
    /// from alphaInfo (alpha-first vs alpha-last) × byteOrder32Little
    /// (which reverses the in-memory component order) instead of
    /// hard-coding RGBA.
    private func rgbaAt(_ img: UIImage, _ x: Int, _ y: Int) -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat) {
        guard let cg = img.cgImage, let data = cg.dataProvider?.data as Data? else { return (-1, -1, -1, -1) }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        let idx = y * bpr + x * bpp
        guard bpp == 4, idx + 3 < data.count else { return (-1, -1, -1, -1) }
        // Raw component bytes as laid out in memory.
        let c0 = CGFloat(data[idx]) / 255, c1 = CGFloat(data[idx + 1]) / 255
        let c2 = CGFloat(data[idx + 2]) / 255, c3 = CGFloat(data[idx + 3]) / 255
        // Alpha position from alphaInfo; little-endian 32-bit storage
        // reverses the declared order (ARGB → BGRA in memory, etc.).
        let alphaFirst = [.premultipliedFirst, .first, .noneSkipFirst].contains(cg.alphaInfo)
        let little = cg.bitmapInfo.contains(.byteOrder32Little)
        switch (alphaFirst, little) {
        case (true, false):  return (c1, c2, c3, c0)  // ARGB
        case (true, true):   return (c2, c1, c0, c3)  // BGRA
        case (false, false): return (c0, c1, c2, c3)  // RGBA
        case (false, true):  return (c3, c2, c1, c0)  // ABGR (RGBA reversed)
        }
    }

    // MARK: - pins

    /// The diagnosed bug: a slice-`fill` center must paint BENEATH the
    /// element's content. Content here is a 20×20 GREEN square centered
    /// in a 60×60 element (the rest transparent, standing in for the
    /// text label); the border image is all-red with `fill`. Correct
    /// paint order → the center sample is GREEN (content atop the fill)
    /// while a border-band sample is RED (the nine-slice shows through
    /// where no content covers it). Under the old `.overlay` the center
    /// sample came back red — content buried under the fill.
    @MainActor
    func testSliceFillCenterPaintsBeneathContent() throws {
        // Pure channel green, NOT SwiftUI's `Color.green` — that is
        // systemGreen (52, 199, 89), whose green channel (0.78) sits
        // below the 0.9 assertion floor and whose red channel (0.20)
        // false-fails the not-red pin even when the paint order is
        // correct (the exact first-run failure of this test).
        let view = Rectangle().fill(Color(red: 0, green: 1, blue: 0))
            // Small centered "content" so border bands stay uncovered.
            .frame(width: 20, height: 20)
            .frame(width: 60, height: 60)
            .modifier(BorderImageApplier(config: fillConfig()))
        let img = try XCTUnwrap(render(view, size: CGSize(width: 60, height: 60)),
                                "ImageRenderer produced no image")
        // Center (30,30): inside both the fill center and the content.
        let center = rgbaAt(img, 30, 30)
        XCTAssertGreaterThan(center.g, 0.9,
                             "center must be GREEN — the slice-fill center is covering the content (overlay paint order)")
        XCTAssertLessThan(center.r, 0.1,
                          "center must not be red — border-image fill painted above content")
        // Border band (4,30): inside the 8px left band, outside content.
        let band = rgbaAt(img, 4, 30)
        XCTAssertGreaterThan(band.r, 0.9,
                             "left border band must be RED — did the nine-slice stop painting after the .background move?")
        XCTAssertLessThan(band.g, 0.1, "left border band must not pick up the content color")
    }

    /// The §6.4 guarantee: an outset band paints OUTSIDE the element.
    /// This pin found a PRE-EXISTING culling bug (present under the old
    /// `.overlay` too, empirically bisected wave 5): GraphicsContext
    /// culls draw(Image, in:) calls outside the Canvas's own bounds
    /// (fill(Path) escapes, image draws do not), so negative-origin
    /// band draws never rasterized. The applier now expands the Canvas
    /// itself over the outset area via negative per-edge padding, which
    /// keeps every image draw in-bounds. Host is a clear 40×40 box
    /// centered in a 60×60 canvas (host spans 10…50); an 8px outset
    /// expands the border image area to 2…58, so the top band (8px wide
    /// → y 2…10) paints strictly outside the host's frame.
    @MainActor
    func testOutsetStillPaintsOutsideHostBoundsAsBackground() throws {
        let host = Color.clear
            .frame(width: 40, height: 40)
            .modifier(BorderImageApplier(config: fillConfig(outset: 8)))
            // Outer transparent stage so the outside-the-host samples
            // land within the rasterized canvas.
            .frame(width: 60, height: 60)
        let img = try XCTUnwrap(render(host, size: CGSize(width: 60, height: 60)),
                                "ImageRenderer produced no image")
        // (30,5): above the host's top edge (y=10), inside the outset
        // band (y 2…10) — must be red, proving no implicit clip.
        let outside = rgbaAt(img, 30, 5)
        XCTAssertGreaterThan(outside.r, 0.9,
                             "outset band above the host must paint — image draws outside the Canvas bounds are culled, so the Canvas must span the outset area (negative padding)")
        // (30,0): beyond the outset area entirely — must stay empty.
        XCTAssertLessThan(rgbaAt(img, 30, 0).a, 0.1,
                          "beyond the outset area must remain transparent")
    }
}
