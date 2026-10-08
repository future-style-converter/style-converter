//
//  ComposedCanvasIcbClipTests.swift
//  StyleConverterTestTests
//
//  wave-52 lane L2 (Fix A) — structural pins that the composed canvas's
//  INLINE-AXIS CLIP is WIRED where the contract says. The geometry itself is
//  the runtime's pure WPTCanvas.icbClipBand / IcbClipBand, pinned under the
//  Catalyst destination in runtimes/swiftui ComposedIcbClipTests; this file
//  pins the modifier chain of ComposedCaptureCanvas.body, which no
//  device-free test can render (ImageRenderer needs a simulator — the same
//  reason OutOfFlowAnchorTests pins predicates, not pixels).
//
//  THE CONTRACT (page-padding-overrun brief §4 Fix A, iOS): the band clip is
//  applied with `.clipShape` to ONE group that contains the in-flow VStack
//  chain AND BOTH `FixedHoistOverlay` attach points (the negative-z
//  `.background(alignment:)` half and the positive-z `.overlay(alignment:)`
//  half), and it is attached BEFORE `.background(canvasBackground)` so the
//  frame keeps the propagated body colour. The Shape is fed the FRAME
//  (`Self.padding` = WPTCanvas.canvasFramePx), never `resolvedPadding` —
//  CSS 2.1 §9.1.1: an author `body { padding }` moves neither the viewport
//  nor its crop.
//
//  Source is read from THIS test bundle — project.yml copies
//  StyleConverterTest/Screenshot/CaptureCanvas.swift in with a Copy Files
//  phase (`buildPhase: copyFiles: destination: resources`, dstSubfolderSpec
//  7 — NOT `buildPhase: resources`, whose Copy Bundle Resources phase
//  silently drops a .swift file) (wave 53, harness-hygiene T1-a: the
//  bundle runs in the simulator, which is refused the host checkout, so
//  the old `#filePath` read failed every pin here with NSCocoaErrorDomain
//  257) — CODE LINES
//  ONLY: `//` lines are dropped so prose that mentions a modifier cannot
//  satisfy a pin. This is the Swift twin of the Android harness's
//  ComposedCanvasIcbClipSourceTest.
//
//  Two more wiring pins of the same composed body ride here (one source-scan
//  file per canvas): wave-52 L2 T3 — the RC1 static-position root's
//  `.zIndex(1)` (CSS 2.1 Appendix E step 8; align-items-007's abspos green
//  under the later red `<img>`), and M1 — the split reads the ONE-OWNER
//  body-margin strip and the flow pad adds the body margin's inset.
//
//  EXECUTED MUTATIONS — this target needs a simulator (rule: no device), so
//  the XCTest itself is NOT run in lane L2; the SAME slicing and assertions
//  are replayed by tools/titan/results/wave52-composed-canvas/
//  ios-source-pins.mjs (executed, with these mutations; mutations.log):
//  IOS-M1 `.clipShape(...)` moved AFTER `.background(canvasBackground)` →
//  testClipPrecedesTheCanvasBackground red; IOS-M2 the positive-z `.overlay`
//  moved back after `.rootCanvasClip` → testBothOverlayHalvesAreInsideTheClip
//  red; IOS-M3 `.zIndex(1)` deleted → testRc1StaticPositionRootPaintsAboveTheFlow
//  red; IOS-M4 the split fed `document.components` unstripped →
//  testSplitReadsTheOneOwnerBodyMargin red; IOS-M5 the T6 `.map` dropped →
//  testSplitReadsTheOneOwnerBodyMargin red; IOS-M6 the lift gate forced
//  (`if aboveFlow[idx] {` → `if true {`) and IOS-M7 the gate list reverted to
//  the first cut's bare RC1 flag → testRc1LiftIsGatedByTheWholeListRule red
//  (the skeptic fix: a `z-index: -1` RC1 box must stay below the flow).
//  Wave 53: with the source a bundle resource the XCTest itself runs; its
//  first executed Swift mutation (IOS-M1 on the real file) is recorded in
//  tools/titan/results/wave53-harness-hygiene/_note.md.
//

import XCTest
@testable import StyleConverterTest

final class ComposedCanvasIcbClipTests: XCTestCase {

    /// The harness canvas source, as copied into this test bundle at build
    /// time (project.yml's Copy Files phase, `copyFiles: destination:
    /// resources` — never `buildPhase: resources`, which drops a .swift
    /// silently) — the same bytes the app
    /// target compiles. No disk fallback and no skip guard: a missing
    /// resource is a real failure (finding A8#3), never a silent skip.
    private func canvasSource() throws -> String {
        let url = try XCTUnwrap(
            Bundle(for: Self.self).url(forResource: "CaptureCanvas", withExtension: "swift"),
            "CaptureCanvas.swift is not a resource of StyleConverterTestTests — project.yml")
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// The COMPOSED canvas's `body` only, code lines only.
    private func composedBodyCode() throws -> String {
        let source = try canvasSource()
        // Slice: the composed struct's declaration → the hooks modifier after it.
        let start = "struct ComposedCaptureCanvas: View {"
        let end = "struct DynamicCaptureHooks: ViewModifier {"
        XCTAssertTrue(source.contains(start), "ComposedCaptureCanvas declaration missing")
        XCTAssertTrue(source.contains(end), "DynamicCaptureHooks declaration missing")
        let region = source.components(separatedBy: start).dropFirst().joined(separator: start)
            .components(separatedBy: end).first ?? ""
        // Drop `//` comment lines so prose never satisfies a wiring pin.
        return region.split(separator: "\n", omittingEmptySubsequences: false)
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            .joined(separator: "\n")
    }

    /// Index of `needle` in `hay`, or nil — for ordering pins.
    private func offset(of needle: String, in hay: String) -> Int? {
        hay.range(of: needle).map { hay.distance(from: hay.startIndex, to: $0.lowerBound) }
    }

    func testClipShapeIsAppliedExactlyOnceWithTheFrame() throws {
        // One band clip in the composed body, fed the FRAME — never the
        // resolved pad (a pad-fed clip would move with `body { padding }`).
        let code = try composedBodyCode()
        XCTAssertEqual(code.components(separatedBy: ".clipShape(WPTCanvas.IcbClipBand(frame: Self.padding))").count - 1, 1)
        XCTAssertFalse(code.contains("IcbClipBand(frame: pad"))
        XCTAssertFalse(code.contains("IcbClipBand(frame: resolvedPadding"))
    }

    func testClipPrecedesTheCanvasBackground() throws {
        // The clip is attached BEFORE `.background(canvasBackground)`: the
        // background stays OUTSIDE the clip, so the frame keeps the
        // propagated body colour (mutation M1 red).
        let code = try composedBodyCode()
        let clip = try XCTUnwrap(offset(of: ".clipShape(WPTCanvas.IcbClipBand", in: code))
        let bg = try XCTUnwrap(offset(of: ".background(canvasBackground)", in: code))
        XCTAssertLessThan(clip, bg, "the ICB band clip must precede the canvas background")
    }

    func testBothOverlayHalvesAreInsideTheClip() throws {
        // BOTH FixedHoistOverlay attach points precede the clip in the
        // chain, so hoisted boxes are cropped at the viewport edge like
        // in-flow overflow (mutation M2 red on the positive-z half).
        let code = try composedBodyCode()
        let clip = try XCTUnwrap(offset(of: ".clipShape(WPTCanvas.IcbClipBand", in: code))
        let behind = try XCTUnwrap(offset(of: ".background(alignment: .topLeading)", in: code))
        let above = try XCTUnwrap(offset(of: ".overlay(alignment: .topLeading)", in: code))
        XCTAssertLessThan(behind, clip, "the negative-z overlay half must be inside the clip")
        XCTAssertLessThan(above, clip, "the positive-z overlay half must be inside the clip")
        // Exactly two overlay attach points — one per Appendix E half.
        XCTAssertEqual(code.components(separatedBy: "FixedHoistOverlay(components: paint.").count - 1, 2)
    }

    func testFlowStackIsInsideTheClip() throws {
        // The flow VStack's frame/padding chain precedes the clip too — the
        // clip wraps content, it does not sit between the VStack and its pad.
        let code = try composedBodyCode()
        let clip = try XCTUnwrap(offset(of: ".clipShape(WPTCanvas.IcbClipBand", in: code))
        // The flow stack's pad + body-margin inset (wave-52 L2 M1 folded the
        // margin into the same `.padding(EdgeInsets(...))` call).
        let pad = try XCTUnwrap(offset(of: ".padding(EdgeInsets(top: pad.top + marginInset.top", in: code))
        XCTAssertLessThan(pad, clip, "the padded flow stack must be inside the clip")
    }
    func testRc1StaticPositionRootPaintsAboveTheFlow() throws {
        // wave-52 L2 (T3) — CSS 2.1 Appendix E step 8: the RC1 slot (anchored
        // by StaticPositionAnchor) carries `.zIndex(1)` so it draws after the
        // later in-flow roots of the VStack (mutation IOS-M3 red).
        let code = try composedBodyCode()
        let anchor = try XCTUnwrap(offset(of: ".modifier(StaticPositionAnchor())", in: code))
        let z = try XCTUnwrap(offset(of: ".zIndex(1)", in: code))
        XCTAssertLessThan(anchor, z, "the zIndex must ride the anchored RC1 host")
        XCTAssertEqual(code.components(separatedBy: ".zIndex(1)").count - 1, 1)
    }

    func testRc1LiftIsGatedByTheWholeListRule() throws {
        // wave-52 L2 (T3 skeptic fix) — the `.zIndex(1)` sits ONLY in the
        // branch gated by the runtime's whole-list rule (no declared z, a
        // single painted box, no later step-8 root); every other RC1 root
        // keeps the wave-51 VStack order (IOS-M6 / IOS-M7 red).
        let code = try composedBodyCode()
        XCTAssertTrue(code.contains("let aboveFlow = UABlockMargin.composedRootsPaintingAboveFlow(split.flow)"))
        let gate = try XCTUnwrap(offset(of: "if aboveFlow[idx] {", in: code))
        let z = try XCTUnwrap(offset(of: ".zIndex(1)", in: code))
        XCTAssertLessThan(gate, z, "the zIndex must sit inside the gated branch")
        // The gated branch ends at its own `} else {` — the zIndex precedes it.
        let tail = String(code[code.index(code.startIndex, offsetBy: gate)...])
        let elseAt = try XCTUnwrap(offset(of: "} else {", in: tail))
        XCTAssertLessThan(z - gate, elseAt, "the zIndex leaked out of the gated branch")
        // Every rootBody call site hands the list down (three walks).
        XCTAssertEqual(code.components(separatedBy: "aboveFlow: aboveFlow)").count - 1, 3)
    }

    func testSplitReadsTheOneOwnerBodyMargin() throws {
        // wave-52 L2 (M1) — the roots are split with the body-root's owned
        // margin sides stripped (else the fold re-emits the margin the pad now
        // carries — collapsed-border's 60 twice; mutation IOS-M4 red), and the
        // flow pad adds the margin's positive inset per side.
        let code = try composedBodyCode()
        XCTAssertTrue(code.contains("FixedHoist.split(roots: UABlockMargin.withCanvasOwnedBodyMargin("))
        XCTAssertTrue(code.contains("UABlockMargin.canvasBodyMargin(document.components)"))
        XCTAssertTrue(code.contains(".padding(EdgeInsets(top: pad.top + marginInset.top,"))
        // wave-52 L2 (T6): the same rewrite hands hoisted UA-tag roots their
        // UA block margin (mutation IOS-M5 red).
        XCTAssertTrue(code.contains(".map(UABlockMargin.withUaBlockMarginOnHoistedRoot))"))
    }
}
