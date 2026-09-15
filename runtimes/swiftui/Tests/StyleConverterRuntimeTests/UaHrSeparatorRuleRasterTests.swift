//
//  UaHrSeparatorRuleRasterTests.swift
//  Wave 50, fix lane F2 (on skeptic S7's B7-1 finding) — the raster pin
//  under lane B7's `ua-hr-separator-box` extractor patch.
//
//  WHY THIS PIN EXISTS. B7's patch (BACKLOG queue 5(a),
//  `tools/titan/results/wave50-B7/ua-hr-separator-box.patch`) stops a
//  rule-less `<hr>` from shipping as a 100×100 white square and bakes the
//  UA box instead. Its predicted flip is +3 cells on
//  `css-images/gradient/gradient-hue-direction` (f 0.6241 / 0.6235 /
//  0.6230 on web / iOS / Android). Skeptic S7 replayed that prediction
//  through the campaign's own scorer and found it hinges on ONE thing B7
//  never measured: removing the three 98px displacements alone lands both
//  natives BELOW the bar (web P 0.9521, iOS f 0.9491, Android f 0.9495);
//  with a 2px rule actually PAINTED the cells go P 1.0000 / 0.9970 /
//  0.9973. S7's sensitivity sweep says ANY painted rule passes and a
//  MISSING one fails — so "does SwiftUI paint the baked bag at all" is
//  the whole flip, and it is a runtime question, not an extractor one.
//  This test answers it on this platform.
//
//  THE PAYLOAD is the baked bag itself, in the wire shapes the converter
//  emits — `display:block; height:0px; box-sizing:content-box;
//  border-{top,right,bottom,left}-width:1px; border-style:inset;
//  border-color:#eeeeee`, and NO `Width`, which is the point: the rule
//  must take its width from the containing block. The two shorthands
//  expand per side in the converter (BorderStyleExpander /
//  BorderColorExpander, 1 value → 4 longhands), and every leaf shape here
//  is copied from a frozen wave49-final per-test IR document
//  (`{"type":"BorderTopWidth","data":{"px":1}}`,
//  `{"type":"BorderTopStyle","data":"SOLID"}`,
//  `{"type":"BoxSizing","data":"CONTENT_BOX"}`, …) so the decoders see
//  exactly what the gate will hand them. B7's `margin-block` and
//  `overflow` keys are NOT here: they place the rule inside the document
//  and cannot change the band's own height, width or shading, and this
//  pin is about the band.
//
//  THE STAGE is the composed-WPT capture environment the corpus renders
//  in — `wptCaptureMode` + `wptBlockFlowFillWidth = 358`, the 390px
//  canvas minus the body's 16px margins, which is the containing block a
//  corpus `<hr>` actually gets (same two keys as
//  AbsposFlexOverlayRasterTests' stage).
//
//  EXPECTED GEOMETRY (CSS 2.1 §10.6.3 + css-backgrounds-3 §3.2 under
//  Blink's shading, the arithmetic B7 measured off the frozen ref PNG
//  `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//  white-black-ink-font-lh-imgpad-htmlpins/css-images/
//  gradient__gradient-hue-direction.png` at y=188/189, x 16–373):
//    * content height 0 + 1px top border + 1px bottom border ⇒ a band
//      exactly 2px tall;
//    * `width: auto` on a block box ⇒ the border box fills the 358px
//      containing block;
//    * `border-style: inset` ⇒ top/left take Blink's DARK band and
//      bottom/right keep the declared colour (BorderSideApplier's
//      `.inset` branch: `dark = side == .top || side == .left`).
//      Dark(#EEE) = 238 × max(0,(0.93333−0.33)/0.93333) = 153.8 → 154;
//      the light band does not lift because
//      BlinkBorderShade.lightBandLifts early-outs on red ≥ 150 (238 ≥
//      150), so the bottom row stays 238. That is exactly the
//      rgb(154) over rgb(238) pair B7 read off the ref.
//
//  MEASURED (Catalyst, wave 50 fix lane F2, this file unmodified):
//  ink box `(w: 358, h: 2, minX: 0, minY: 0, maxX: 357, maxY: 1)`; row 0
//  is rgb(154,154,154) across, row 1 is rgb(238,238,238), the left column
//  is 154 and the right column 238, row 2 onward is the white stage. So
//  SwiftUI DOES paint the baked bag, and B7's +3 prediction is not
//  blocked on this platform. (The frozen ref's rgb(196) corner mitres are
//  a Blink trapezoid blend the per-side painter does not reproduce; two
//  pixels at each end of a 358px rule, far below any scorer threshold,
//  and out of this pin's scope.)
//
//  MUTATION PROOF — two, each cp/sha256/edit/run/restore (file sha256
//  fa3a39720568f9c0f06c56bb63b002f12d8e717157e1dccfc490e6cfe1056ffe
//  before and after):
//   1. drop the four `Border*Style` properties (the "author declined the
//      bake" shape) → BOTH tests fail on their unwrap, "XCTUnwrap failed
//      … no ink at all". The pin cannot pass without a painted rule.
//   2. `Height` 0px → 10px (the un-baked shape) →
//      testBakedHrPaintsATwoPixelRule fails `("12") is not equal to
//      ("2") +/- ("1")`. 12 = 10 content + the 2px border band, which is
//      also the live proof that `box-sizing: content-box` inflates here
//      — the reason B7 states that key explicitly.
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class UaHrSeparatorRuleRasterTests: XCTestCase {

    // MARK: - The baked bag, flat v2 wire

    /// One component, no children, no text — a `<hr>` wearing exactly the
    /// UA bake and nothing else. `#eeeeee` is 238/255 = 0.9333333333333333
    /// in the IR's sRGB floats; `original` is carried verbatim the way the
    /// converter emits it.
    static let hrBakedIR = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"hr-001","name":"hr","properties":[\
    {"type":"Display","data":"BLOCK"},\
    {"type":"Height","data":{"type":"length","px":0.0}},\
    {"type":"BoxSizing","data":"CONTENT_BOX"},\
    {"type":"BorderTopWidth","data":{"px":1.0}},\
    {"type":"BorderRightWidth","data":{"px":1.0}},\
    {"type":"BorderBottomWidth","data":{"px":1.0}},\
    {"type":"BorderLeftWidth","data":{"px":1.0}},\
    {"type":"BorderTopStyle","data":"INSET"},\
    {"type":"BorderRightStyle","data":"INSET"},\
    {"type":"BorderBottomStyle","data":"INSET"},\
    {"type":"BorderLeftStyle","data":"INSET"},\
    {"type":"BorderTopColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},\
    {"type":"BorderRightColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},\
    {"type":"BorderBottomColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},\
    {"type":"BorderLeftColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}}\
    ]}
    ]}
    """

    /// The containing-block width a corpus `<hr>` gets: the 390px capture
    /// canvas minus the browser-ref's 16px body margins.
    private static let containingBlockWidth: CGFloat = 358

    // MARK: - Raster plumbing (AbsposFlexOverlayRasterTests pattern)

    /// Decode through the runtime's own loader — one root, no slots.
    private func root(_ ir: String) throws -> IRComponent {
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data(ir.utf8))
        return try XCTUnwrap(doc.components.first { $0.slot == nil })
    }

    /// Rasterise at scale 1 on a WHITE stage as wide as the containing
    /// block, top-leading so the rule's border box starts at raster y=0.
    /// Bytes are read back through a CGContext redraw because
    /// ImageRenderer's native buffer layout is not guaranteed.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = ComponentRenderer(component: comp)
            .frame(width: Self.containingBlockWidth, height: 40, alignment: .topLeading)
            .background(Color.white)
            // The composed-WPT capture environment: `width: auto` on a
            // block box only takes the §10.3.3 containing-block fill on
            // this path (ComponentRenderer.styledContent's
            // wptBlockFlowFillWidth fold), which is the path the gate
            // captures on.
            .environment(\.wptCaptureMode, true)
            .environment(\.wptBlockFlowFillWidth, Self.containingBlockWidth)
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

    /// RGB at a raster point.
    private func rgb(_ img: (px: [UInt8], w: Int, h: Int), _ x: Int, _ y: Int)
        -> (r: Int, g: Int, b: Int) {
        let i = (y * img.w + x) * 4
        return (Int(img.px[i]), Int(img.px[i + 1]), Int(img.px[i + 2]))
    }

    /// The bounding box of NON-WHITE ink. White is the stage, so "any
    /// pixel that is not the stage" is the rule — a tolerance of 6 per
    /// channel below 255 keeps the near-white light band (238) firmly
    /// inside the ink set while rejecting stage antialiasing.
    private func inkBox(_ img: (px: [UInt8], w: Int, h: Int))
        -> (w: Int, h: Int, minX: Int, minY: Int, maxX: Int, maxY: Int)? {
        var minX = Int.max, minY = Int.max, maxX = -1, maxY = -1
        for y in 0..<img.h {
            for x in 0..<img.w {
                let c = rgb(img, x, y)
                guard c.r < 249 || c.g < 249 || c.b < 249 else { continue }
                minX = min(minX, x); minY = min(minY, y)
                maxX = max(maxX, x); maxY = max(maxY, y)
            }
        }
        return maxX >= 0
            ? (maxX - minX + 1, maxY - minY + 1, minX, minY, maxX, maxY)
            : nil
    }

    // MARK: - The pin

    /// The band's GEOMETRY: 2px tall, the full 358px containing block
    /// wide. ±1px on each axis absorbs a half-pixel stroke centring, and
    /// nothing else — the failure this guards against is a 0px band (the
    /// `height: 0` collapsing the border box, which is what
    /// `box-sizing: content-box` is in the bake to prevent) or no band at
    /// all (the bake not painting), both of which S7 measured as the
    /// difference between a pass and a fail on all three platforms.
    @MainActor
    func testBakedHrPaintsATwoPixelRule() throws {
        let img = try render(try root(Self.hrBakedIR))
        let box = try XCTUnwrap(inkBox(img),
                                "no ink at all — the baked <hr> box painted nothing, "
                                + "so B7's +3 prediction does not hold on iOS")
        XCTAssertEqual(box.h, 2, accuracy: 1,
                       "rule height \(box.h): 0 means the border box collapsed, "
                       + ">3 means the 0px content height was not honoured")
        XCTAssertEqual(box.w, Int(Self.containingBlockWidth), accuracy: 1,
                       "rule width \(box.w): a block box with width:auto must fill "
                       + "its 358px containing block")
        XCTAssertEqual(box.minX, 0, accuracy: 1, "rule does not start at the content edge")
        XCTAssertEqual(box.minY, 0, accuracy: 1, "rule does not start at the top of the box")
    }

    /// The band's SHADING: Blink's two-tone `inset` palette. The top row
    /// is Dark(#EEE) = 154 and the bottom row is the declared 238 (the
    /// light band does not lift — red 238 ≥ 150 early-outs
    /// BlinkBorderShade.lightBandLifts). Sampled mid-span so the corner
    /// mitres, where the left and right edges meet these rows, are out of
    /// frame. ±3 per channel covers stroke antialiasing against white.
    @MainActor
    func testInsetShadingMatchesBlinksTwoTonePalette() throws {
        let img = try render(try root(Self.hrBakedIR))
        let box = try XCTUnwrap(inkBox(img), "no ink — nothing to shade")
        let midX = (box.minX + box.maxX) / 2
        let top = rgb(img, midX, box.minY)
        let bottom = rgb(img, midX, box.maxY)
        XCTAssertEqual(top.r, 154, accuracy: 3,
                       "top edge \(top): inset must take Blink's DARK band, 238 × "
                       + "max(0,(0.93333−0.33)/0.93333) = 154")
        XCTAssertEqual(bottom.r, 238, accuracy: 3,
                       "bottom edge \(bottom): inset's light band is the declared "
                       + "#EEE unchanged (the contrast gate early-outs at red ≥ 150)")
        // Grey in, grey out — a hue shift would mean the shade ran on the
        // wrong channel model.
        XCTAssertEqual(top.g, top.r, accuracy: 2, "dark band is not neutral grey")
        XCTAssertEqual(bottom.g, bottom.r, accuracy: 2, "light band is not neutral grey")
    }
}
