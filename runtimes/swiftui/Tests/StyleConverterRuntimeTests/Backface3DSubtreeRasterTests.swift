//
//  Backface3DSubtreeRasterTests.swift
//  Wave 20 (lane W4, B-RC4) — raster pins for the iOS 3D-subtree
//  blanking on css-transforms backface-visibility-hidden-001.
//
//  Symptom (wave19-final capture): iOS rendered NOTHING of the 3D
//  subtree — zero green, zero red — and the 0.9641 SSIM only cleared
//  the gate because the subject is ~3% of the canvas (the manifest
//  classifier flagged structural-divergence). The wave-19 culling
//  predicate itself was verified correct (hidden-002 passes honestly).
//
//  Bisection result (probes below, kept as regression pins): the
//  blanking stage was `transform-style: preserve-3d` → `.drawingGroup()`
//  in TransformsApplier Step 3. drawingGroup rasterises the subtree
//  offscreen CLIPPED to the view's bounds; the preserve-3d DIV of
//  hidden-001 holds only absolutely-positioned faces (they render via
//  the renderer's `.overlay` channel, out of flow), so its auto height
//  is 0 and the offscreen pass produced a 200×0 — empty — texture.
//  ImageRenderer is NOT the limitation: a Rotate3DEffect quad with a
//  real perspective row rasterises fine offscreen (pin below), so no
//  manual-matrix workaround is needed.
//
//  Wire shapes are the EXACT live-converter IR from
//  tools/titan/runs/wave19-final/sections/css-transforms/per-test-ir/
//  (flat v2 slot lists). NOTE: IRDocument's decoder ALREADY composes the
//  slot list (IRModels.swift line "components = IRComposer.compose"),
//  so tests consume doc.components directly — calling IRComposer.compose
//  a second time on the composed roots STRIPS their children
//  (withChildren(nil) — the composer sees a one-element flat list with
//  no slot refs) and silently renders a childless tree.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class Backface3DSubtreeRasterTests: XCTestCase {

    // MARK: - Fixture: backface-visibility-hidden-001, verbatim live wire

    /// The full flat component list of hidden-001 minus the caption <p>
    /// (pure text, no geometry): container (200×200, perspective:1000px,
    /// rotateY(45°)) → preserve-3d DIV → red face (rotateY(180°),
    /// backface hidden, must cull) + green face (unrotated, backface
    /// hidden, must paint projected). IRDocument's decode composes the
    /// slot refs — the same path the capture harness walks.
    private func hidden001Container() throws -> IRComponent {
        // Decode the wire bytes — converter shapes, not hand idealisations.
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion":2,"minReaderVersion":2,"components":[
          {"id":"wpt__css-transforms__backface-visibility-hidden-001__1-041",
           "name":"wpt__css-transforms__backface-visibility-hidden-001__1",
           "properties":[
             {"type":"Width","data":{"type":"length","px":200}},
             {"type":"Height","data":{"type":"length","px":200}},
             {"type":"Perspective","data":{"type":"length","px":1000}},
             {"type":"Transform","data":{"type":"functions","list":[{"fn":"rotateY","a":{"deg":45}}]}}
           ]},
          {"id":"backface-visibility-hidden-001__1__0-042",
           "name":"backface-visibility-hidden-001__1__0",
           "properties":[{"type":"TransformStyle","data":"PRESERVE_3D"}],
           "slot":{"parent":"wpt__css-transforms__backface-visibility-hidden-001__1-041"}},
          {"id":"backface-visibility-hidden-001__1__0__0-043",
           "name":"backface-visibility-hidden-001__1__0__0",
           "properties":[
             {"type":"Position","data":"ABSOLUTE"},
             {"type":"Top","data":{"px":50}},
             {"type":"Left","data":{"px":50}},
             {"type":"Width","data":{"type":"length","px":100}},
             {"type":"Height","data":{"type":"length","px":100}},
             {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},
             {"type":"ZIndex","data":{"value":1,"original":{"type":"integer","value":1}}},
             {"type":"Transform","data":{"type":"functions","list":[{"fn":"rotateY","a":{"deg":180}}]}},
             {"type":"BackfaceVisibility","data":"HIDDEN"}
           ],
           "slot":{"parent":"backface-visibility-hidden-001__1__0-042"}},
          {"id":"backface-visibility-hidden-001__1__0__1-044",
           "name":"backface-visibility-hidden-001__1__0__1",
           "properties":[
             {"type":"Position","data":"ABSOLUTE"},
             {"type":"Top","data":{"px":50}},
             {"type":"Left","data":{"px":50}},
             {"type":"Width","data":{"type":"length","px":100}},
             {"type":"Height","data":{"type":"length","px":100}},
             {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},
             {"type":"ZIndex","data":{"value":2,"original":{"type":"integer","value":2}}},
             {"type":"BackfaceVisibility","data":"HIDDEN"}
           ],
           "slot":{"parent":"backface-visibility-hidden-001__1__0-042"}}
        ]}
        """.utf8))
        // Decode already composed the slot list; the root IS the container.
        return try XCTUnwrap(doc.components.first)
    }

    // MARK: - Raster plumbing (AbsposFlexOverlayRasterTests pattern)

    /// Rasterise any view at scale 1 into an RGBA8 buffer — one buffer
    /// pixel per logical point, format-stable byte reads via a CGContext
    /// redraw (ImageRenderer's native buffer layout is not guaranteed).
    @MainActor
    private func raster<V: View>(_ view: V) throws -> (px: [UInt8], w: Int, h: Int) {
        let renderer = ImageRenderer(content: view)
        // Scale 1 mirrors the capture harness's point-for-pixel canvas.
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

    /// Wrap the real component tree the way the composed-WPT capture
    /// does: top-leading 240×240 white stage (container border box at
    /// raster (0,0); white makes "nothing painted" assertable) plus the
    /// harness environment — the blanking only manifests on this path.
    @MainActor
    private func renderStage(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        try raster(
            ComponentRenderer(component: comp)
                // Top-leading: the container's border box pins to (0,0).
                .frame(width: 240, height: 240, alignment: .topLeading)
                .background(Color.white)
                // Composed-capture environment (captureComposedDocument).
                .environment(\.wptCaptureMode, true)
                .environment(\.wptBlockFlowFillWidth, 358))
    }

    /// Ink census: green / red pixel counts plus the green bounding box —
    /// the classifier-level facts the wave-19 capture failed on (zero of
    /// both colors) and the geometry pin the fix is judged by.
    private func inkStats(_ img: (px: [UInt8], w: Int, h: Int))
        -> (green: Int, red: Int, box: (minX: Int, minY: Int, maxX: Int, maxY: Int)?) {
        var green = 0, red = 0
        var minX = Int.max, minY = Int.max, maxX = -1, maxY = -1
        for y in 0..<img.h {
            for x in 0..<img.w {
                let i = (y * img.w + x) * 4
                let r = Int(img.px[i]), g = Int(img.px[i + 1]), b = Int(img.px[i + 2])
                // CSS `green` is rgb(0,128,0); wide tolerance absorbs the
                // projective resample's edge antialiasing.
                if r < 100, g > 80, g < 180, b < 100 {
                    green += 1
                    minX = min(minX, x); minY = min(minY, y)
                    maxX = max(maxX, x); maxY = max(maxY, y)
                }
                // CSS `red` is rgb(255,0,0) — ANY surviving red pixel is
                // a culling failure (WPT: "no red").
                if r > 180, g < 80, b < 80 { red += 1 }
            }
        }
        return (green, red, maxX >= 0 ? (minX, minY, maxX, maxY) : nil)
    }

    // MARK: - B-RC4 pin: the full hidden-001 tree paints correctly

    /// Green face orthographically flattened, red face culled, on the exact
    /// live wire. css-transforms-2 §8 / §4.1.1: the container's `perspective`
    /// PROPERTY is its CHILDREN's perspective, never a factor of its OWN
    /// rotateY(45°) (retro F3 — the wave-5 seed folded it in and this pin
    /// asserted the resulting keystone, y ≈ [48, 152]; the frozen WPT ref
    /// draws exactly 100 rows in every column). §4.1 flatten: face X spans
    /// ±50 about the origin (100,100) → x' = 100 ∓ 50·cos45 = [64.6, 135.4]
    /// → raster x ≈ [65, 134]; y is untouched → raster y ≈ [50, 149].
    @MainActor
    func testHidden001GreenFaceProjectedAndRedFaceCulled() throws {
        let img = try renderStage(try hidden001Container())
        let s = inkStats(img)
        // 1. The subtree paints AT ALL — the wave-19 capture had ZERO
        //    green pixels anywhere (the vacuous 0.9641 pass).
        let box = try XCTUnwrap(s.box, "no green ink — 3D subtree blanked again")
        // 2. No red survives anywhere: the 225°-accumulated red face is
        //    culled by the (untouched) wave-19 predicate.
        XCTAssertEqual(s.red, 0, "red backface leaked through culling")
        // 3. Geometry: the green quad sits at the CSS-projected bbox
        //    (±4px tolerance for the projective edge antialiasing).
        XCTAssertEqual(box.minX, 65, accuracy: 4, "left edge off the orthographic flatten")
        XCTAssertEqual(box.maxX, 134, accuracy: 4, "right edge off the orthographic flatten")
        XCTAssertEqual(box.minY, 50, accuracy: 2, "top edge keystoned — the property must not project the element itself")
        XCTAssertEqual(box.maxY, 149, accuracy: 2, "bottom edge keystoned — the property must not project the element itself")
        // 4. Coverage sanity: the projected quad area is ≈ 71 × 100+ —
        //    demand most of it (no hairline or single-row artifacts).
        XCTAssertGreaterThan(s.green, 5000, "green ink far below projected quad area")
    }

    // MARK: - Guard: ImageRenderer is NOT the limitation

    /// A Rotate3DEffect quad with a REAL perspective row (m34 ≠ 0)
    /// rasterises offscreen — disproves the "SwiftUI cannot rasterise
    /// the projected quad via ImageRenderer" hypothesis from the lane
    /// brief, so no manual ProjectionTransform workaround is warranted.
    /// If a future OS regresses this, the pin fails and the workaround
    /// becomes the fix.
    @MainActor
    func testProjectedQuadRasterizesOffscreen() throws {
        // 100×100 green quad, centered on a 240 stage (center = 120),
        // under perspective(1000px) rotateY(45°) — the hidden-001 pair.
        let img = try raster(
            Rectangle().fill(Color(red: 0, green: 0.5, blue: 0))
                .frame(width: 100, height: 100)
                .modifier(Rotate3DEffect(axisX: 0, axisY: 1, axisZ: 0, deg: 45,
                                         perspectivePx: 1000, anchor: .center))
                .frame(width: 240, height: 240)
                .background(Color.white))
        let s = inkStats(img)
        // The projected quad about center 120: x ≈ [83, 154], keystoned
        // y ≈ [68, 172] — assert presence + the measured bbox (±4px).
        let box = try XCTUnwrap(s.box, "projected quad did not rasterise offscreen")
        XCTAssertEqual(box.minX, 83, accuracy: 4)
        XCTAssertEqual(box.maxX, 153, accuracy: 4)
        XCTAssertEqual(box.minY, 68, accuracy: 4)
        XCTAssertEqual(box.maxY, 171, accuracy: 4)
    }

    // MARK: - Mechanism pin: why Step 3's drawingGroup had to go

    /// `.drawingGroup()` clips out-of-bounds overlay ink to the wrapped
    /// view's bounds — on a zero-height box (the hidden-001 preserve-3d
    /// DIV shape: auto height 0, faces on the overlay channel) that is
    /// TOTAL blanking; without it the same composition paints fully.
    /// Documents the removal rationale in executable form; if SwiftUI
    /// ever changes drawingGroup's clipping, this pin flags it for
    /// re-evaluation (the removal stays correct either way — CSS never
    /// flattens or clips at a preserve-3d boundary, css-transforms-2 §7).
    @MainActor
    func testDrawingGroupClipsZeroHeightOverlaySubtree() throws {
        // The hidden-001 preserve-3d DIV skeleton: 200×0 flow box whose
        // only ink is a 100×100 overlay face offset to (50,50).
        func zeroHeightWithOverlay() -> some View {
            Color.clear
                .frame(width: 200, height: 0)
                .overlay(alignment: .topLeading) {
                    Rectangle().fill(Color(red: 0, green: 0.5, blue: 0))
                        .frame(width: 100, height: 100)
                        .offset(x: 50, y: 50)
                }
        }
        // Wrapped in drawingGroup (the removed Step-3 behavior): blank.
        let clipped = try raster(
            zeroHeightWithOverlay()
                .drawingGroup()
                .frame(width: 240, height: 240, alignment: .topLeading)
                .background(Color.white))
        XCTAssertEqual(inkStats(clipped).green, 0,
                       "drawingGroup no longer clips — re-evaluate Step 3 removal note")
        // Without it (current engine behavior): the full 100×100 paints.
        let free = try raster(
            zeroHeightWithOverlay()
                .frame(width: 240, height: 240, alignment: .topLeading)
                .background(Color.white))
        XCTAssertEqual(inkStats(free).green, 10000,
                       "overlay ink must escape the zero-height flow box")
    }

    // MARK: - Culling predicate untouched: hidden-002 still honest

    /// hidden-002's two flat components (no 3D context): the red
    /// rotateY(180°) backface-hidden box culls, the green rotateY(180°)
    /// visible-backface box paints — the honest wave-19 pass must
    /// survive the Step-3 change byte-for-byte.
    @MainActor
    func testHidden002RedCulledGreenPaints() throws {
        // Verbatim wire components (hidden-002 per-test IR, roots only).
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion":2,"minReaderVersion":2,"components":[
          {"id":"wpt__css-transforms__backface-visibility-hidden-002__1-046",
           "name":"wpt__css-transforms__backface-visibility-hidden-002__1",
           "properties":[
             {"type":"Width","data":{"type":"length","px":100}},
             {"type":"Height","data":{"type":"length","px":100}},
             {"type":"Transform","data":{"type":"functions","list":[{"fn":"rotateY","a":{"deg":180}}]}},
             {"type":"BackfaceVisibility","data":"HIDDEN"},
             {"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}
           ]},
          {"id":"wpt__css-transforms__backface-visibility-hidden-002__2-047",
           "name":"wpt__css-transforms__backface-visibility-hidden-002__2",
           "properties":[
             {"type":"Width","data":{"type":"length","px":100}},
             {"type":"Height","data":{"type":"length","px":100}},
             {"type":"Transform","data":{"type":"functions","list":[{"fn":"rotateY","a":{"deg":180}}]}},
             {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}
           ]}
        ]}
        """.utf8))
        // Stack the two roots like document flow (red above green).
        let roots = doc.components
        let img = try raster(
            VStack(alignment: .leading, spacing: 0) {
                // Each root renders through the full engine chain.
                ForEach(Array(roots.enumerated()), id: \.offset) { _, r in
                    ComponentRenderer(component: r)
                }
            }
            .frame(width: 240, height: 240, alignment: .topLeading)
            .background(Color.white)
            .environment(\.wptCaptureMode, true)
            .environment(\.wptBlockFlowFillWidth, 358))
        let s = inkStats(img)
        // Red is backface-hidden at 180° → culled; green (backface
        // visible, initial value) paints its mirrored 100×100.
        XCTAssertEqual(s.red, 0, "hidden-002 red face must stay culled")
        XCTAssertEqual(s.green, 10000, "hidden-002 green face must paint fully")
    }
}
