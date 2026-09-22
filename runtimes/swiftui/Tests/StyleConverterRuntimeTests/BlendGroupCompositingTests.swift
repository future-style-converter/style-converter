//
//  BlendGroupCompositingTests.swift
//  Wave 50, post-gate iOS FIX lane — the raster pin under BlendModeApplier's
//  `.compositingGroup()`. Re-targeted in wave 51 PR (A), see below.
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
//  WHAT WAS WRONG (measured in wave 50 on the Catalyst raster, on a byte
//  mirror of the harness CaptureCanvas that is byte-IDENTICAL to the
//  simulator captures it predicts — 5/5 opacity-blend crops and both
//  committed visual-test blend baselines diffed at 0 px):
//  a blended box multiplied its own label glyphs against its own background
//  rectangle. The wave-50 device gate failed on exactly that, at
//  fixtures/combinations/blend-isolation.json 003_wrapper —
//  iOS-Android / iOS-web SSIM 0.9492, Δpx 0.60%, ΔE95 0.00.
//
//  WAVE 51 PR (A) RE-TARGET. The label is harness CHROME now — drawn by the
//  capture canvas as a sibling overlay, never inside the element — so the
//  element's own paint no longer contains label glyphs and pin 2's original
//  subject (label ink at (24,19,33)) is gone by design; pin 1's flat #66ccff
//  layer has no second primitive either, so it lost its mutation power. Pin
//  2 therefore gets a REAL second primitive: a 20×20 `#00ffff` child inside
//  the multiply box. Correct group render: child src-over onto the box →
//  (0,255,255), then the GROUP × the #1A1A2E stage → (0,26,46); the box
//  fill itself stays (24,8,11). Mutation (drop `.compositingGroup()` at
//  BlendModeApplier.swift:66, EXECUTED wave 51 on the Catalyst raster):
//  child centre (46,46) read (0,8,11) exactly, 400 px of (0,8,11), 0 px of
//  (0,26,46) — pin 2 red on all three assertions; pin 1 stayed green under
//  the same mutation, which is why it is no longer the guard. Bytes below
//  are read off the raster (the exact centre probe is the measurement).
//
//  The mirror plumbing lives in CaptureCanvasMirror.swift (hoisted so the
//  PR (A) chrome pins compose onto the same chain).
//
//  Payload: the converter's verbatim IR (`./gradlew :converter:run
//  --args="convert --from css --to ir -i <fixture> -o <dir>"`, committed
//  converter @ this tree), whitespace compacted, one component per line;
//  the cyan child is hand-added in the same wire shape (slot-linked).
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class BlendGroupCompositingTests: XCTestCase {
    typealias M = CaptureCanvasMirror

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

    /// visual-test.json's `BlendMode_Multiply` (087: padding-only `#e74c3c`
    /// box, `mix-blend-mode: multiply`) PLUS a 20×20 `#00ffff` in-flow child
    /// (wave 51 re-target, design C46) — the second primitive inside the
    /// group whose colour tells "grouped then blended" from "each primitive
    /// blended in turn". Box = 20 pad + 20 child = 60×60 at canvas (16,16);
    /// the child fills (36,36)…(55,55).
    static let blendMultiplyIR = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"blendmode_multiply-087","name":"BlendMode_Multiply","properties":[{"type":"MixBlendMode","data":"MULTIPLY"},{"type":"BackgroundColor","data":{"srgb":{"r":0.9058823529411765,"g":0.2980392156862745,"b":0.23529411764705882},"original":"#e74c3c"}},{"type":"PaddingTop","data":{"px":20.0}},{"type":"PaddingRight","data":{"px":20.0}},{"type":"PaddingBottom","data":{"px":20.0}},{"type":"PaddingLeft","data":{"px":20.0}}]},
    {"id":"cyan-088","name":"cyan","properties":[{"type":"Width","data":{"type":"length","px":20.0}},{"type":"Height","data":{"type":"length","px":20.0}},{"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":1.0,"b":1.0},"original":"#00ffff"}}],"slot":{"parent":"blendmode_multiply-087"}}
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

    // MARK: - Pin 1: the isolation equality the gate row broke

    /// compositing-1 §3 + §5.1: an isolated group's backdrop is transparent
    /// black, alpha_b = 0 collapses `co = alpha_s · B(Cb,Cs) + …` to
    /// `alpha_s · Cs`, and the inner layer therefore paints UNCHANGED — the
    /// same pixels the blend-normal control paints. Kept as the fixture's
    /// own contract; since PR (A) the flat layer carries no second primitive,
    /// so pin 2 below is the executable guard of the wave-50 fix.
    @MainActor
    func testIsolatedWrapperRendersLikeTheBlendNormalControl() throws {
        let iso  = try M.outOfFlowCanvas(try comp(Self.blendIsolationIR, "wrapper-005"))
        let ctrl = try M.outOfFlowCanvas(try comp(Self.blendIsolationIR, "wrapper-008"))
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

    /// The element's in-flow child must composite NORMALLY against the
    /// element's own background and only then multiply with the backdrop.
    /// Measured on the raster (wave 51): the grouped cyan child over the
    /// #1A1A2E stage is (0,26,46) — 400 px, the whole 20×20 child — and the
    /// box fill around it is (24,8,11). Without the group the child
    /// multiplies against the already-multiplied box and reads (0,8,11):
    /// that value is forbidden outright, and the group value must be there.
    @MainActor
    func testBlendedElementDoesNotMultiplyItsOwnInk() throws {
        let img = try M.flowCanvas(try comp(Self.blendMultiplyIR, "blendmode_multiply-087"))
        // Probe the child's centre first, EXACT, so a failure names the actual
        // byte (this is the raster measurement the header quotes).
        let centre = M.rgb(img, 46, 46)
        XCTAssertTrue(centre == (0, 26, 46),
                      "child centre (46,46) is \(centre), expected the GROUP value (0,26,46)")
        // The group value covers the whole child (400 px; ≥ 380 tolerates
        // nothing but a stray edge byte) …
        XCTAssertGreaterThanOrEqual(M.count(img, (0, 26, 46), tol: 1), 380,
                                    "the group-composited child (0,26,46) is not on the canvas — "
                                    + ".compositingGroup() is missing")
        // … and the per-primitive leak value is absent everywhere.
        XCTAssertEqual(M.count(img, (0, 8, 11), tol: 1), 0,
                       "found the child multiplied against the element's OWN "
                       + "already-multiplied background")
        // The box fill is unaffected by grouping (a flat rect has nothing of
        // its own beneath it) — the wave-50 measurement, still true.
        XCTAssertTrue(M.rgb(img, 20, 20) == (24, 8, 11), "box fill at (20,20) is \(M.rgb(img, 20, 20))")
    }
}
