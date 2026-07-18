//
//  ContainingBlockHeightTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 9 — pins the containing-block HEIGHT channel and the abspos
//  padding-box basis fix:
//    • channel + context defaults are nil (indefinite — the ScrollView
//      rationale keeps percent heights skipped at the root), and the
//      always-rewrite reset discipline has an honest nil to reset TO;
//    • the SizeApplier height axis flips allowPercent ON only when a
//      definite basis is present (CSS 2.1 §10.5);
//    • ContainingBlockBasis.paddingBox keeps the ancestor's padding
//      INSIDE the basis (css-position-3 §3.1 — the containing block of
//      an abspos child is the PADDING box) while contentBox (in-flow
//      children) still subtracts it — the wave-2 arithmetic unchanged;
//    • end-to-end: an abspos `width/height: 100%` child of a definite
//      70×80 parent actually paints (the wave-9 gate capture showed the
//      PARENT through a zero-area child).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ContainingBlockHeightTests: XCTestCase {

    // MARK: - Helpers

    /// Decode a property list from wire JSON — keeps the fixtures on the
    /// exact byte shapes the converter emits (all shapes below pinned
    /// against live converter output, wave 9).
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// Build the engine style for a property-list JSON.
    private func style(_ json: String) throws -> ComponentStyle {
        StyleBuilder.build(from: try props(json))
    }

    /// Decode an IRComponent from nested wire JSON (no memberwise init —
    /// the decode path IS the production path).
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    // MARK: - Channel defaults (reset discipline's nil)

    /// The environment channel defaults to nil: a root under the
    /// unbounded ScrollView has NO height basis, so percent heights keep
    /// the pre-wave-9 skip (documented ScrollView rationale). This nil
    /// is also what the always-rewrite discipline resets children to.
    func testChannelDefaultsToNil() {
        XCTAssertNil(EnvironmentValues().containingBlockHeight,
                     "root default must be nil (indefinite basis)")
        XCTAssertNil(SpacingContext().containingBlockHeightPx,
                     "context default must be nil (indefinite basis)")
    }

    // MARK: - allowPercent gating (CSS 2.1 §10.5)

    /// No definite basis → `height: 100%` must stay skipped (auto), even
    /// though a viewport height exists — vh-only, never a percent basis.
    func testPercentHeightSkipsWithoutBasis() throws {
        // Pinned wire: percent Height is {"type":"percentage","value":N}.
        let s = try style("""
        [{"type":"Height","data":{"type":"percentage","value":100.0}}]
        """)
        XCTAssertNil(ContainingBlockBasis.definiteBorderBox(style: s, vertical: true),
                     "percent height with nil basis must degrade to auto")
    }

    /// A definite ancestor basis (threaded via SpacingContext) lets the
    /// percent height resolve against it — 100% of 80 is 80, 50% is 40.
    func testPercentHeightResolvesAgainstDefiniteBasis() throws {
        var s = try style("""
        [{"type":"Height","data":{"type":"percentage","value":100.0}}]
        """)
        // The parent published an 80px containing-block height.
        s.spacing.context.containingBlockHeightPx = 80
        XCTAssertEqual(ContainingBlockBasis.definiteBorderBox(style: s, vertical: true), 80)
        var half = try style("""
        [{"type":"Height","data":{"type":"percentage","value":50.0}}]
        """)
        half.spacing.context.containingBlockHeightPx = 80
        XCTAssertEqual(ContainingBlockBasis.definiteBorderBox(style: half, vertical: true), 40)
    }

    /// The resolver-level gate SizeApplier rides: percent stays nil when
    /// allowPercent is false and resolves when the caller flips it on
    /// with a definite basis as the parent length.
    func testResolveLevelAllowPercentGate() {
        let ctx = SpacingContext()
        // height: 100% as the extractor produces it.
        let pct = LengthValue.relative(value: 100, unit: .percent, pxFallback: nil)
        // Legacy skip (no basis): nil even with a parent length passed.
        XCTAssertNil(SizeApplierResolve.exact(pct, ctx: ctx, parent: 844,
                                              allowPercent: false))
        // Definite basis (wave 9): resolves to 100% of the basis.
        XCTAssertEqual(SizeApplierResolve.exact(pct, ctx: ctx, parent: 80,
                                                allowPercent: true), 80)
    }

    // MARK: - Padding-box vs content-box basis (css-position-3 §3.1)

    /// A 70×80 parent with 8px padding: in-flow children resolve against
    /// the CONTENT box (54×64 — wave-2 arithmetic unchanged), abspos
    /// children against the PADDING box (70×80 — no painted border, so
    /// padding box == border box), matching the wave-8 overlay anchor.
    func testPaddingBoxKeepsAncestorPaddingInsideBasis() throws {
        // Pinned wire: length sizes {"type":"length","px":N}, padding
        // longhands {"px":N} (converter output for `padding: 8px`).
        let s = try style("""
        [{"type":"Width","data":{"type":"length","px":70.0}},
         {"type":"Height","data":{"type":"length","px":80.0}},
         {"type":"PaddingTop","data":{"px":8.0}},
         {"type":"PaddingRight","data":{"px":8.0}},
         {"type":"PaddingBottom","data":{"px":8.0}},
         {"type":"PaddingLeft","data":{"px":8.0}}]
        """)
        // In-flow basis: border box − padding band (both axes).
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: false), 54)
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: true), 64)
        // Abspos basis: the padding stays INSIDE the containing block.
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: false), 70)
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: true), 80)
    }

    /// Indefinite parents publish nil on BOTH basis flavors — the
    /// channels then reset children to nil (no invented geometry, and
    /// no grandparent leak through the always-rewrite discipline).
    func testIndefiniteParentPublishesNilBases() throws {
        // No Width/Height at all → fit-content parent.
        let s = try style("""
        [{"type":"PaddingTop","data":{"px":8.0}}]
        """)
        XCTAssertNil(ContainingBlockBasis.contentBox(style: s, vertical: false))
        XCTAssertNil(ContainingBlockBasis.contentBox(style: s, vertical: true))
        XCTAssertNil(ContainingBlockBasis.paddingBox(style: s, vertical: false))
        XCTAssertNil(ContainingBlockBasis.paddingBox(style: s, vertical: true))
    }

    // MARK: - End-to-end pixel proof (the gate capture)

    /// Render a component at scale 1 on a white canvas and return the
    /// RGBA byte at (x, y) — same CGContext harness as WPTCaptureModeTests.
    @MainActor
    private func pixel(_ comp: IRComponent, x: Int, y: Int) throws -> (r: UInt8, g: UInt8, b: UInt8) {
        // Product-path render (no WPT flag): a plain topLeading canvas.
        let view = ComponentRenderer(component: comp)
            .frame(width: 200, height: 150, alignment: .topLeading)
            .background(Color.white)
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
        let i = (y * w + x) * 4
        return (buf[i], buf[i + 1], buf[i + 2])
    }

    /// The wave-9 gate scenario: a definite 70×80 red parent with an
    /// abspos `width/height: 100%` blue child. Pre-fix the child's
    /// percent height was skipped (intrinsic 0 — nothing painted) and
    /// the probe saw the PARENT's red; post-fix the child covers the
    /// parent's padding box and the probe must be blue.
    @MainActor
    func testAbsposFullPercentChildPaintsOverDefiniteParent() throws {
        // Wire pinned against live converter output for
        // `position:relative; width:70px; height:80px; background:#f00`
        // with an abspos `top:0; left:0; width:100%; height:100%; #00f`
        // child (nested-children shape the runtime decodes directly).
        let comp = try component("""
        {"id":"p","name":"AbsParent",
         "properties":[
           {"type":"Position","data":"RELATIVE"},
           {"type":"Width","data":{"type":"length","px":70.0}},
           {"type":"Height","data":{"type":"length","px":80.0}},
           {"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":"#ff0000"}}
         ],
         "children":[
           {"id":"c","name":"AbsChild",
            "properties":[
              {"type":"Position","data":"ABSOLUTE"},
              {"type":"Top","data":{"px":0.0}},
              {"type":"Left","data":{"px":0.0}},
              {"type":"Width","data":{"type":"percentage","value":100.0}},
              {"type":"Height","data":{"type":"percentage","value":100.0}},
              {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.0,"b":1.0},"original":"#0000ff"}}
            ]}
         ]}
        """)
        // Probe the parent's center — well inside both boxes, away from
        // any anti-aliased edge at scale 1.
        let p = try pixel(comp, x: 35, y: 40)
        // Blue child on top ⇒ blue dominant, red ≈ 0. Pre-fix this was
        // pure red (the parent showing through the zero-area child).
        XCTAssertLessThan(p.r, 64, "parent red must be covered by the abspos child")
        XCTAssertGreaterThan(p.b, 192, "abspos 100%/100% child must paint blue over the parent")
    }
}
