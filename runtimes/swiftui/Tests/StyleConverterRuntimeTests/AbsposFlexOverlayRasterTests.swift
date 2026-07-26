//
//  AbsposFlexOverlayRasterTests.swift
//  Wave 19 (lane FLEX, RC-A5b) — raster pin for the abspos child of a
//  RELATIVE flex parent: css-flexbox dynamic-align-self-001's green
//  100×100 child (position:absolute; top:0; left:0; align-self:end)
//  must paint at the parent's padding-box origin — the explicit insets
//  REPLACE the static position on both axes (css-position-3 §3.5), so
//  the align-self claim is inert. On the wave18-final capture iOS never
//  painted the child at all (web+ref paint green over the red probe).
//
//  Wire shapes are the EXACT live-converter IR from
//  tools/titan/runs/wave18-final/sections/css-flexbox/per-test-ir/
//  wpt__css-flexbox__dynamic-align-self-001.json (component __2 + its
//  slot child), trimmed only of the zero-valued border color/style
//  noise that does not affect geometry.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class AbsposFlexOverlayRasterTests: XCTestCase {

    // MARK: - Fixture (live IR of dynamic-align-self-001 __2 subtree)

    /// The RELATIVE flex parent (200×200, transparent) holding the
    /// green abspos child (100×100, top:0/left:0, align-self:end) —
    /// decoded from wire JSON so the test sees converter byte shapes.
    /// FULL property lists, verbatim from the live capture (zero-valued
    /// margins/paddings/borders included — the vanish must reproduce on
    /// the exact wire, not a trimmed idealization).
    private func dynamicAlignSelfTree() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"dyn-parent-021","name":"DynParent",
         "properties":[
           {"type":"Position","data":"RELATIVE"},
           {"type":"Display","data":"FLEX"},
           {"type":"Width","data":{"type":"length","px":200.0}},
           {"type":"Height","data":{"type":"length","px":200.0}},
           {"type":"Top","data":{"px":0.0}},
           {"type":"Left","data":{"px":0.0}},
           {"type":"BoxSizing","data":"CONTENT_BOX"},
           {"type":"MarginTop","data":{"px":0.0}},
           {"type":"MarginRight","data":{"px":0.0}},
           {"type":"MarginBottom","data":{"px":0.0}},
           {"type":"MarginLeft","data":{"px":0.0}},
           {"type":"PaddingTop","data":{"px":0.0}},
           {"type":"PaddingRight","data":{"px":0.0}},
           {"type":"PaddingBottom","data":{"px":0.0}},
           {"type":"PaddingLeft","data":{"px":0.0}},
           {"type":"BorderTopWidth","data":{"px":0.0}},
           {"type":"BorderRightWidth","data":{"px":0.0}},
           {"type":"BorderBottomWidth","data":{"px":0.0}},
           {"type":"BorderLeftWidth","data":{"px":0.0}},
           {"type":"BorderTopStyle","data":"NONE"},
           {"type":"BorderRightStyle","data":"NONE"},
           {"type":"BorderBottomStyle","data":"NONE"},
           {"type":"BorderLeftStyle","data":"NONE"},
           {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":0.0},
                                             "original":{"r":0,"g":0,"b":0,"a":0}}},
           {"type":"OverflowX","data":"VISIBLE"},
           {"type":"OverflowY","data":"VISIBLE"}
         ],
         "children":[
           {"id":"dyn-child-001","name":"G",
            "properties":[
              {"type":"Display","data":"FLEX"},
              {"type":"Position","data":"ABSOLUTE"},
              {"type":"Width","data":{"type":"length","px":100.0}},
              {"type":"Height","data":{"type":"length","px":100.0}},
              {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.5019607843137255,"b":0.0},
                                                "original":{"r":0,"g":128,"b":0}}},
              {"type":"AlignSelf","data":"END"},
              {"type":"Top","data":{"px":0.0}},
              {"type":"Left","data":{"px":0.0}},
              {"type":"BoxSizing","data":"CONTENT_BOX"},
              {"type":"MarginTop","data":{"px":0.0}},
              {"type":"MarginRight","data":{"px":0.0}},
              {"type":"MarginBottom","data":{"px":0.0}},
              {"type":"MarginLeft","data":{"px":0.0}},
              {"type":"PaddingTop","data":{"px":0.0}},
              {"type":"PaddingRight","data":{"px":0.0}},
              {"type":"PaddingBottom","data":{"px":0.0}},
              {"type":"PaddingLeft","data":{"px":0.0}},
              {"type":"BorderTopWidth","data":{"px":0.0}},
              {"type":"BorderRightWidth","data":{"px":0.0}},
              {"type":"BorderBottomWidth","data":{"px":0.0}},
              {"type":"BorderLeftWidth","data":{"px":0.0}},
              {"type":"BorderTopStyle","data":"NONE"},
              {"type":"BorderRightStyle","data":"NONE"},
              {"type":"BorderBottomStyle","data":"NONE"},
              {"type":"BorderLeftStyle","data":"NONE"},
              {"type":"OverflowX","data":"VISIBLE"},
              {"type":"OverflowY","data":"VISIBLE"}
            ]}
         ]}
        """.utf8))
    }

    // MARK: - Raster plumbing (PositionedContainerPaintOrderTests pattern)

    /// Rasterize on a 240×240 WHITE stage at scale 1 with a top-leading
    /// anchor — the parent's border box sits at raster (0,0), so the
    /// green child's expected ink is exactly (0,0)–(100,100). White
    /// makes "nothing painted here" an assertable color.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = ComponentRenderer(component: comp)
            // Top-leading stage: parent border box at raster (0,0).
            .frame(width: 240, height: 240, alignment: .topLeading)
            .background(Color.white)
            // The composed-WPT capture environment (mirrors the harness's
            // captureComposedDocument + ComposedCaptureCanvas): the
            // wave18 failure only manifests on this path — the same tree
            // painted fine with wptCaptureMode unset.
            .environment(\.wptCaptureMode, true)
            .environment(\.wptBlockFlowFillWidth, 358)
        // Scale 1 = one buffer pixel per logical point, like the harness.
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // Redraw into a known RGBA8 layout for format-stable byte reads.
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

    /// Loose color match — antialiasing/blending tolerance of ±24.
    private func matches(_ c: (r: UInt8, g: UInt8, b: UInt8),
                         _ t: (r: Int, g: Int, b: Int)) -> Bool {
        abs(Int(c.r) - t.r) <= 24 && abs(Int(c.g) - t.g) <= 24 && abs(Int(c.b) - t.b) <= 24
    }

    // MARK: - RC-A5b pin

    /// The green child paints, and paints at the containing block's
    /// origin: `top:0; left:0` beat the align-self claim on BOTH axes
    /// (css-position-3 §3.5 — a non-auto inset replaces the static
    /// position), so ink covers (0,0)–(100,100) and does NOT sit in the
    /// bottom band the (inert) align-self:end would have chosen.
    @MainActor
    func testInsetAnchoredAbsposChildOfRelativeFlexParentPaintsAtOrigin() throws {
        let img = try render(try dynamicAlignSelfTree())
        let green = (r: 0, g: 128, b: 0)
        let white = (r: 255, g: 255, b: 255)
        // 1. The child paints AT ALL (the wave18 iOS capture had zero
        //    green pixels anywhere on the canvas).
        XCTAssertTrue(matches(rgb(img, 50, 50), green),
                      "child ink missing at (50,50): \(rgb(img, 50, 50))")
        // 2. Anchored at the padding-box origin, not the align-self:end
        //    static position (which would start at y=100).
        XCTAssertTrue(matches(rgb(img, 2, 2), green),
                      "child ink missing at origin: \(rgb(img, 2, 2))")
        XCTAssertTrue(matches(rgb(img, 97, 97), green),
                      "child ink missing at (97,97): \(rgb(img, 97, 97))")
        // 3. Nothing paints below/right of the 100×100 box — the
        //    align-self:end band (y 100–200) must stay white.
        XCTAssertTrue(matches(rgb(img, 50, 150), white),
                      "align-self:end leaked static alignment: \(rgb(img, 50, 150))")
        XCTAssertTrue(matches(rgb(img, 150, 50), white),
                      "unexpected ink right of the child: \(rgb(img, 150, 50))")
    }

    // MARK: - RC-A5b paint order: negative-z hoisted boxes go BEHIND flow

    /// The z:-1 red probe of dynamic-align-self-001, as a hoisted root
    /// (abspos + insets → FixedHoist F2 class), 100×100 at (0,0) —
    /// exactly covering the green child's expected rect.
    private func redProbe() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"red-probe-001","name":"R",
         "properties":[
           {"type":"Position","data":"ABSOLUTE"},
           {"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0},
                                             "original":{"r":255,"g":0,"b":0}}},
           {"type":"Width","data":{"type":"length","px":100.0}},
           {"type":"Height","data":{"type":"length","px":100.0}},
           {"type":"ZIndex","data":{"value":-1,"original":{"type":"integer","value":-1}}},
           {"type":"Top","data":{"px":0.0}},
           {"type":"Left","data":{"px":0.0}},
           {"type":"BoxSizing","data":"CONTENT_BOX"}
         ]}
        """.utf8))
    }

    /// Pure classifier pin: the paint partition routes negative-z boxes
    /// to the BEHIND half and everything else to the ABOVE half, both
    /// halves in document order (Appendix E step 3 vs step 8).
    func testPaintPartitionSplitsByZIndexSign() throws {
        let red = try redProbe()
        let green = try dynamicAlignSelfTree()
        let split = FixedHoist.paintPartition([red, green])
        // z:-1 → behind; the (z-less) flex parent → above.
        XCTAssertEqual(split.behind.map(\.id), ["red-probe-001"])
        XCTAssertEqual(split.above.map(\.id), ["dyn-parent-021"])
    }

    /// Raster pin for the composed-canvas mount contract: the hoisted
    /// z:-1 red probe mounts as a `.background` layer (step 3 — behind
    /// ALL in-flow content) while the in-flow flex parent's positioned
    /// overlay paints the green child ABOVE it — so green covers red
    /// pixel-for-pixel, like the web capture (green square, no red).
    /// On the wave-17 single-overlay mount the red probe painted OVER
    /// the green child instead (the wave18 iOS dynamic-align-self-001
    /// failure: zero green pixels anywhere). The view here replicates
    /// the ComposedCaptureCanvas layering exactly: flow content, then
    /// `.background { FixedHoistOverlay(behind) }`, then white, then
    /// `.overlay { FixedHoistOverlay(above) }`.
    @MainActor
    func testNegativeZHoistedRootPaintsBehindPositionedOverlayChild() throws {
        let red = try redProbe()
        let flowParent = try dynamicAlignSelfTree()
        // The canvas-side split: red hoists (abspos root with insets),
        // the relative flex parent stays in flow.
        let split = FixedHoist.split(roots: [red, flowParent])
        XCTAssertEqual(split.hoisted.map(\.id), ["red-probe-001"])
        let paint = FixedHoist.paintPartition(split.hoisted)
        // The composed-canvas layer order, runtime views only.
        let view = ZStack(alignment: .topLeading) {
            ForEach(Array(split.flow.enumerated()), id: \.offset) { _, root in
                ComponentHost(component: root)
            }
        }
        .frame(width: 240, height: 240, alignment: .topLeading)
        // Step 3: negative-z hoisted boxes behind the flow content…
        .background(alignment: .topLeading) {
            if !paint.behind.isEmpty { FixedHoistOverlay(components: paint.behind) }
        }
        // …above only the canvas background (white).
        .background(Color.white)
        // Step 8: everything else stays the wave-17 overlay.
        .overlay(alignment: .topLeading) {
            if !paint.above.isEmpty { FixedHoistOverlay(components: paint.above) }
        }
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
        let img = (px: buf, w: w, h: h)
        // Green covers red across the shared 100×100 rect…
        XCTAssertTrue(matches(rgb(img, 50, 50), (r: 0, g: 128, b: 0)),
                      "green must cover the z:-1 red probe: \(rgb(img, 50, 50))")
        XCTAssertTrue(matches(rgb(img, 5, 5), (r: 0, g: 128, b: 0)),
                      "green missing at the shared origin: \(rgb(img, 5, 5))")
        // …and no red survives anywhere in it (web paints no red at all).
        XCTAssertFalse(matches(rgb(img, 95, 95), (r: 255, g: 0, b: 0)),
                       "red probe leaked above the green child")
    }
}
