//
//  BlendGroupCompositingTests.swift
//  Wave 50, post-gate iOS FIX lane — the raster pin under BlendModeApplier's
//  `.compositingGroup()`.
//
//  THE RULE. compositing-1 §5.1: a non-`normal` `mix-blend-mode` makes the
//  element a stacking context, so the element's OWN paint — background,
//  borders, text, in-flow descendants — composites together into one group
//  (compositing-1 §3.1) and only that finished group is blended with the
//  backdrop. SwiftUI's `.blendMode(_:)` alone does the opposite: it is an
//  attribute that propagates to every leaf drawing operation, so each
//  primitive blends against whatever is already in the destination —
//  including the element's own earlier primitives. `.compositingGroup()`
//  flattens the subtree first, which is exactly the §3.1/§5.1 split.
//
//  WHAT WAS WRONG (measured this lane on the Catalyst raster, on a byte
//  mirror of the harness CaptureCanvas that is byte-IDENTICAL to the
//  simulator captures it predicts — 5/5 opacity-blend crops and both
//  committed visual-test blend baselines diffed at 0 px):
//  a blended box multiplied its own label glyphs against its own background
//  rectangle. The wave-50 device gate failed on exactly that, at
//  fixtures/combinations/blend-isolation.json 003_wrapper —
//  iOS-Android / iOS-web SSIM 0.9492, Δpx 0.60%, ΔE95 0.00: the flat fill
//  already agreed on all three platforms and ONLY the 75 glyph pixels did
//  not (0.60% of that 390×32 crop).
//
//  THE TWO PINS BELOW are the two halves of the fixture's own contract and
//  of the committed web baseline. Both were EXECUTED red before the fix and
//  green after (mutation: drop `.compositingGroup()` from BlendModeApplier
//  → pin 1 reports 75 differing pixels, pin 2 finds the self-multiplied ink
//  (23,8,10) and none of the group ink (24,19,33)).
//
//  Payload: the converter's verbatim IR (`./gradlew :converter:run
//  --args="convert --from css --to ir -i <fixture> -o <dir>"`, committed
//  converter @ this tree), whitespace compacted, one component per line.
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class BlendGroupCompositingTests: XCTestCase {

    /// blend-isolation's `BI_Isolate` and `BI_NoBlend_Control` subtrees. The
    /// two wrappers carry the SAME 188×68 `#66ccff` child; the only
    /// difference is isolate+multiply against isolated-transparent backdrop
    /// vs auto+normal. compositing-1 §3 + §5.1 with alpha_b = 0 makes the
    /// blend term vanish, so the two crops must be byte-identical — the
    /// fixture's `_comment` states that equality IS the isolation semantics.
    static let blendIsolationIR = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"bi_isolate-004","name":"BI_Isolate","properties":[{"type":"Width","data":{"type":"length","px":200.0}},{"type":"Height","data":{"type":"length","px":80.0}},{"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.8,"b":0.0},"original":"#ffcc00"}},{"type":"Position","data":"RELATIVE"}]},
    {"id":"wrapper-005","name":"wrapper","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":0.0}},{"type":"Top","data":{"px":0.0}},{"type":"Width","data":{"type":"length","px":200.0}},{"type":"Height","data":{"type":"length","px":80.0}},{"type":"Isolation","data":"ISOLATE"}],"slot":{"parent":"bi_isolate-004"}},
    {"id":"layer-006","name":"layer","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":6.0}},{"type":"Top","data":{"px":6.0}},{"type":"Width","data":{"type":"length","px":188.0}},{"type":"Height","data":{"type":"length","px":68.0}},{"type":"BackgroundColor","data":{"srgb":{"r":0.4,"g":0.8,"b":1.0},"original":"#66ccff"}},{"type":"MixBlendMode","data":"MULTIPLY"}],"slot":{"parent":"wrapper-005"}},
    {"id":"bi_noblend_control-007","name":"BI_NoBlend_Control","properties":[{"type":"Width","data":{"type":"length","px":200.0}},{"type":"Height","data":{"type":"length","px":80.0}},{"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.8,"b":0.0},"original":"#ffcc00"}},{"type":"Position","data":"RELATIVE"}]},
    {"id":"wrapper-008","name":"wrapper","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":0.0}},{"type":"Top","data":{"px":0.0}},{"type":"Width","data":{"type":"length","px":200.0}},{"type":"Height","data":{"type":"length","px":80.0}},{"type":"Isolation","data":"AUTO"}],"slot":{"parent":"bi_noblend_control-007"}},
    {"id":"layer-009","name":"layer","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":6.0}},{"type":"Top","data":{"px":6.0}},{"type":"Width","data":{"type":"length","px":188.0}},{"type":"Height","data":{"type":"length","px":68.0}},{"type":"BackgroundColor","data":{"srgb":{"r":0.4,"g":0.8,"b":1.0},"original":"#66ccff"}},{"type":"MixBlendMode","data":"NORMAL"}],"slot":{"parent":"wrapper-008"}}
    ]}
    """

    /// visual-test.json's `BlendMode_Multiply` — the committed-baseline row
    /// (`tools/visual/baseline/{iOS,web}__087_BlendMode_Multiply.png`). Its
    /// label ink is the whole pin: 59 glyph pixels that the fix moves onto
    /// the web baseline's value exactly.
    static let blendMultiplyIR = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"blendmode_multiply-087","name":"BlendMode_Multiply","properties":[{"type":"MixBlendMode","data":"MULTIPLY"},{"type":"BackgroundColor","data":{"srgb":{"r":0.9058823529411765,"g":0.2980392156862745,"b":0.23529411764705882},"original":"#e74c3c"}},{"type":"PaddingTop","data":{"px":20.0}},{"type":"PaddingRight","data":{"px":20.0}},{"type":"PaddingBottom","data":{"px":20.0}},{"type":"PaddingLeft","data":{"px":20.0}}]}
    ]}
    """

    // MARK: - Harness-mirror plumbing

    /// Decode through the runtime's real loader (the decoder composes the
    /// slot list) and walk the composed tree — the capture harness renders
    /// every node of that tree as its own PNG, children included.
    private func comp(_ ir: String, _ id: String) throws -> IRComponent {
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data(ir.utf8))
        func find(_ list: [IRComponent]) -> IRComponent? {
            for c in list {
                if c.id == id { return c }
                if let kids = c.children, let hit = find(kids) { return hit }
            }
            return nil
        }
        return try XCTUnwrap(find(doc.components), "no component \(id)")
    }

    /// Rasterise at scale 1 and read the bytes back through a CGContext
    /// redraw, because ImageRenderer's native buffer layout is not
    /// guaranteed (the Backface3DSubtreeRasterTests pattern).
    @MainActor
    private func raster<V: View>(_ view: V) throws -> (px: [UInt8], w: Int, h: Int) {
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

    /// apps/ios-harness `CaptureCanvas.backgroundColor` — the #1A1A2E stage
    /// every committed baseline was captured on.
    private static let ground = Color(red: 0x1A / 255.0, green: 0x1A / 255.0, blue: 0x2E / 255.0)
    /// The harness's published capture geometry (390×844, 358 root CB).
    private static let viewport = StyleViewport(width: 390, height: 844, rootContainingBlock: 358)

    /// CaptureCanvas's in-flow branch: 390 wide, 16pt pad, natural height.
    @MainActor
    private func flowCanvas(_ c: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        try raster(ComponentHost(component: c)
            .frame(maxWidth: 390 - 32, alignment: RootAlignment.alignment(for: c))
            .padding(16)
            .frame(width: 390, alignment: .topLeading)
            .fixedSize(horizontal: false, vertical: true)
            .background(Self.ground)
            .environment(\.styleViewport, Self.viewport))
    }

    /// CaptureCanvas's out-of-flow branch: the collapsed 390×32 card an
    /// abspos capture subject gets, anchored at the card corner because both
    /// axes declare an inset (CSS 2.1 §10.3.7 / §10.6.4).
    @MainActor
    private func outOfFlowCanvas(_ c: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        try raster(Color.clear
            .frame(width: 390, height: 32)
            .overlay(alignment: .topLeading) { ComponentHost(component: c) }
            .clipped()
            .background(Self.ground)
            .environment(\.styleViewport, Self.viewport))
    }

    /// Pixels within `tol` per channel of `rgb`.
    private func count(_ img: (px: [UInt8], w: Int, h: Int),
                       _ rgb: (Int, Int, Int), tol: Int) -> Int {
        var n = 0
        for i in stride(from: 0, to: img.w * img.h * 4, by: 4)
        where abs(Int(img.px[i]) - rgb.0) <= tol
            && abs(Int(img.px[i + 1]) - rgb.1) <= tol
            && abs(Int(img.px[i + 2]) - rgb.2) <= tol { n += 1 }
        return n
    }

    // MARK: - Pin 1: the isolation equality the gate row broke

    /// compositing-1 §3 + §5.1: an isolated group's backdrop is transparent
    /// black, alpha_b = 0 collapses `co = alpha_s · B(Cb,Cs) + …` to
    /// `alpha_s · Cs`, and the inner layer therefore paints UNCHANGED — the
    /// same pixels the blend-normal control paints. Without the group the
    /// multiply still reached the layer's own label ink and 75 pixels
    /// disagreed, which is the 0.60% / SSIM 0.9492 the gate reported.
    @MainActor
    func testIsolatedWrapperRendersLikeTheBlendNormalControl() throws {
        let iso  = try outOfFlowCanvas(try comp(Self.blendIsolationIR, "wrapper-005"))
        let ctrl = try outOfFlowCanvas(try comp(Self.blendIsolationIR, "wrapper-008"))
        XCTAssertEqual(iso.w, ctrl.w); XCTAssertEqual(iso.h, ctrl.h)
        var diff = 0
        for i in stride(from: 0, to: iso.w * iso.h * 4, by: 4)
        where iso.px[i] != ctrl.px[i] || iso.px[i + 1] != ctrl.px[i + 1]
            || iso.px[i + 2] != ctrl.px[i + 2] { diff += 1 }
        XCTAssertEqual(diff, 0,
                       "isolate+multiply must render byte-identically to the "
                       + "blend-normal control; \(diff) px differ — the blend "
                       + "is reaching the element's own paint")
    }

    // MARK: - Pin 2: a blended element does not blend with itself

    /// The element's own label ink must composite NORMALLY against its own
    /// background and only then multiply with the backdrop. Measured values
    /// on the #e74c3c box over the #1A1A2E stage: the group result is
    /// (24,19,33) — byte-equal to `tools/visual/baseline/web__087_
    /// BlendMode_Multiply.png` — while the per-primitive leak darkens the
    /// same glyphs to (23,8,10), the value the committed iOS baseline still
    /// carries and the one this pin forbids.
    @MainActor
    func testBlendedElementDoesNotMultiplyItsOwnInk() throws {
        let img = try flowCanvas(try comp(Self.blendMultiplyIR, "blendmode_multiply-087"))
        // The GROUP ink is the discriminator: (24,19,33) differs from the
        // box fill (24,8,11) by 11 on green and 22 on blue, while the
        // leaked per-primitive ink (23,8,10) is within one LSB of that fill
        // and so cannot be told apart from it by colour alone. Assert the
        // group ink is THERE (the mutation deletes it outright: 0 px) and
        // that the exact leaked value is absent.
        XCTAssertGreaterThan(count(img, (24, 19, 33), tol: 1), 40,
                             "the group-composited label ink (the web "
                             + "baseline's value) is not on the canvas — "
                             + ".compositingGroup() is missing")
        XCTAssertEqual(count(img, (23, 8, 10), tol: 0), 0,
                       "found ink multiplied against the element's OWN "
                       + "background")
    }
}
