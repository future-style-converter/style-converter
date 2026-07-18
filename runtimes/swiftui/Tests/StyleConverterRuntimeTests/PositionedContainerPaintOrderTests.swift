//
//  PositionedContainerPaintOrderTests.swift
//  Wave 8 — lane IOS paint-order + overlay anchoring pins.
//
//  CSS 2.1 Appendix E paints an element's own background and border in
//  steps 2–4 and its positioned descendants in step 8 — ABOVE the
//  ancestor's border. The pre-wave-8 renderer ZStacked the absolute
//  overlay INSIDE the whole applyStyle chain, so the container's border
//  stroke (an `.overlay` in BorderSideApplier) painted ON TOP of its
//  absolutely-positioned children — the confirmed wave-8 capture showed
//  a 3px black border stroked across a yellow abspos child. The fix
//  splits the style chain (applyBoxDecoration / applyGroupEffects) and
//  attaches the overlay between the halves, inset to the PADDING box
//  (css-position-3 §3.1: the containing block of an absolutely
//  positioned box is its positioned ancestor's padding box).
//
//  These pins rasterize a full ComponentRenderer tree via ImageRenderer
//  — the same capture path the harness uses — and assert:
//    1. paint order: child ink covers the border band where they overlap;
//    2. the border still paints where the child does NOT cover it;
//    3. padding-box anchoring: `top:0/left:0` lands INSIDE the border
//       band (border-box anchoring would paint the corner yellow);
//    4. size honoring: the child's declared 69×69 overflows the 50×50
//       container instead of being clamped to it (css-position-3 §2.1:
//       out-of-flow boxes size independently of the ancestor's box).
//
//  Wire-shape literals follow the live converter output already pinned
//  by FidelityWave3Tests (`"Position":"ABSOLUTE"`, `{"px":0.0}` insets)
//  and BordersTests (physical Border*Width/Style/Color longhands).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class PositionedContainerPaintOrderTests: XCTestCase {

    // MARK: - Fixture

    /// The diagnosed geometry: a 50×50 container with a 3px solid BLACK
    /// border on every side, holding one absolutely-positioned 69×69
    /// YELLOW child at `top: 0; left: 0`. Border box spans stage
    /// (0,0)–(50,50); border bands are the outer 3px on each edge; the
    /// child anchors at the padding-box corner (3,3) and spans to
    /// (72,72) — overlapping the right/bottom bands and overflowing the
    /// container. Child name is a single glyph so the debug placeholder
    /// label's dark text stays near (3,3), far from every sample point.
    private func paintOrderTree() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"po-parent-001","name":"PaintOrder",
         "properties":[
           {"type":"Width","data":{"type":"length","px":50.0}},
           {"type":"Height","data":{"type":"length","px":50.0}},
           {"type":"BorderTopWidth","data":{"px":3.0}},
           {"type":"BorderRightWidth","data":{"px":3.0}},
           {"type":"BorderBottomWidth","data":{"px":3.0}},
           {"type":"BorderLeftWidth","data":{"px":3.0}},
           {"type":"BorderTopStyle","data":"SOLID"},
           {"type":"BorderRightStyle","data":"SOLID"},
           {"type":"BorderBottomStyle","data":"SOLID"},
           {"type":"BorderLeftStyle","data":"SOLID"},
           {"type":"BorderTopColor","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0}}},
           {"type":"BorderRightColor","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0}}},
           {"type":"BorderBottomColor","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0}}},
           {"type":"BorderLeftColor","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0}}}
         ],
         "children":[
           {"id":"po-child-002","name":"a",
            "properties":[
              {"type":"Position","data":"ABSOLUTE"},
              {"type":"Top","data":{"px":0.0}},
              {"type":"Left","data":{"px":0.0}},
              {"type":"Width","data":{"type":"length","px":69.0}},
              {"type":"Height","data":{"type":"length","px":69.0}},
              {"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":1.0,"b":0.0},"original":"#ffff00"}}
            ]}
         ]}
        """.utf8))
    }

    // MARK: - Raster plumbing (MarginCollapseTests pattern)

    /// Rasterize the component on a 100×100 WHITE stage at scale 1 —
    /// the stage is larger than the 50×50 container so overflow ink
    /// from the 69×69 child lands INSIDE the raster (ImageRenderer only
    /// keeps pixels within the top-level view's bounds), and white
    /// makes "nothing painted here" an assertable color.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = ComponentRenderer(component: comp)
            // Top-leading stage: container border box at raster (0,0).
            .frame(width: 100, height: 100, alignment: .topLeading)
            .background(Color.white)
        // Scale 1 = one buffer pixel per logical point, like the harness.
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // Redraw into a known RGBA8 layout for format-stable byte reads
        // (ImageRenderer's own backing layout varies across OS builds).
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (buf, w, h)
    }

    /// RGB triple at a raster point (RGBA8 buffer from render above).
    private func rgb(_ img: (px: [UInt8], w: Int, h: Int),
                     _ x: Int, _ y: Int) -> (r: UInt8, g: UInt8, b: UInt8) {
        let i = (y * img.w + x) * 4
        return (img.px[i], img.px[i + 1], img.px[i + 2])
    }

    // Channel thresholds: ±25/255 absorbs colorspace conversion while
    // keeping yellow (255,255,0) / black (0,0,0) / white unambiguous.
    private func isYellow(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool {
        c.r > 230 && c.g > 230 && c.b < 25
    }
    private func isBlack(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool {
        c.r < 25 && c.g < 25 && c.b < 25
    }
    private func isWhite(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool {
        c.r > 230 && c.g > 230 && c.b > 230
    }

    // MARK: - 1. Paint order (Appendix E step 8 above steps 2–4)

    /// THE wave-8 capture pin: where the abspos child overlaps the
    /// container's border band, the CHILD's ink must win — positioned
    /// descendants paint after their ancestor's border (CSS 2.1
    /// Appendix E). Under the pre-fix renderer both samples came back
    /// BLACK: the border stroke was applied above the overlay ZStack.
    @MainActor
    func testAbsposChildPaintsAboveContainerBorder() throws {
        let img = try render(try paintOrderTree())
        // (48,40): mid right border band (x 47…50), inside the child
        // (3…72 both axes), below the child's tiny debug label.
        XCTAssertTrue(isYellow(rgb(img, 48, 40)),
                      "right border band under the abspos child must be YELLOW — the container border is stroking above its positioned descendants (Appendix E inversion)")
        // (40,48): mid bottom border band — same overlap, other edge.
        XCTAssertTrue(isYellow(rgb(img, 40, 48)),
                      "bottom border band under the abspos child must be YELLOW — the container border is stroking above its positioned descendants")
    }

    /// The border must still paint wherever the child does NOT cover it
    /// — the fix moves the child ABOVE the border, it must not stop the
    /// border painting (no silent decoration loss).
    @MainActor
    func testContainerBorderStillPaintsWhereUncovered() throws {
        let img = try render(try paintOrderTree())
        // (30,1): mid top band — the child starts at y=3, so the band
        // itself is exposed and must stay black.
        XCTAssertTrue(isBlack(rgb(img, 30, 1)),
                      "exposed top border band must stay BLACK — did the paint-order split drop the border stroke?")
        // (1,30): mid left band — child starts at x=3, band exposed.
        XCTAssertTrue(isBlack(rgb(img, 1, 30)),
                      "exposed left border band must stay BLACK — did the paint-order split drop the border stroke?")
    }

    /// Padding-box anchoring (css-position-3 §3.1): `top:0; left:0`
    /// anchors at the PADDING box, i.e. just inside the border band —
    /// so the band's top-left corner stays black. If the overlay
    /// anchored at the BORDER box instead, the child would cover the
    /// corner and this sample would read yellow.
    @MainActor
    func testChildAnchorsAtPaddingBoxNotBorderBox() throws {
        let img = try render(try paintOrderTree())
        // (1,1): inside the 3px top-left border corner, OUTSIDE the
        // padding box — the child must not reach it.
        XCTAssertTrue(isBlack(rgb(img, 1, 1)),
                      "border corner must stay BLACK — the abspos child is anchoring at the border box instead of the padding box (css-position-3 §3.1)")
        // (40,40): container interior well past the debug label — the
        // child DOES cover the interior, proving it rendered at all
        // (guards the corner pin against a vacuous no-child render).
        XCTAssertTrue(isYellow(rgb(img, 40, 40)),
                      "container interior must be YELLOW — the abspos child did not render")
    }

    // MARK: - 2. Size honoring (css-position-3 §2.1)

    /// The child's declared 69×69 must NOT be clamped to the 50×50
    /// container: an absolutely positioned box sizes from its own
    /// properties and simply overflows its containing block. SwiftUI
    /// rigid frames ignore proposals, and the overlay attachment must
    /// not clip — this pins both.
    @MainActor
    func testAbsposChildSizeOverflowsContainer() throws {
        let img = try render(try paintOrderTree())
        // (60,60): outside the container's border box (ends at 50),
        // inside the child's span (3…72) — overflow ink must exist.
        XCTAssertTrue(isYellow(rgb(img, 60, 60)),
                      "abspos child ink must extend beyond the container bounds — the 69px declared size is being clamped or clipped to the 50px container")
        // (80,80): beyond the child's span — the stage must stay white,
        // proving the overflow is exactly the declared size, not a
        // runaway fill of the proposal.
        XCTAssertTrue(isWhite(rgb(img, 80, 80)),
                      "beyond the child's declared 69px span the stage must stay WHITE — the child is over-expanding past its declared size")
    }
}
