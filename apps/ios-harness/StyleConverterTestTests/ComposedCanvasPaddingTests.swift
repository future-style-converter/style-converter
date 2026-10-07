//
//  ComposedCanvasPaddingTests.swift
//  StyleConverterTestTests
//
//  wave-24 B-RC5 — device-free unit pins for
//  ComposedCaptureCanvas.resolvedPadding, the composed canvas's per-side
//  pad resolver.
//
//  WHY it exists (wave 24): capture-browser-ref.mjs used to frame every
//  reference page with a ZERO-specificity `:where(body) { padding: 16px }`.
//  Per CSS Selectors L4 §17 `:where()` contributes no specificity, so a ref
//  declaring its OWN `body { padding: 0 }` (0,0,1) WON and rendered with no
//  body pad. The composed canvas hardcoded `Self.padding`, so every such
//  test's whole render sat (+16,+16) off its ref — MEASURED as the ENTIRE
//  divergence of css-masking/clip-path-circle-007, whose test AND ref both
//  open with `body, div { padding: 0; margin: 0 }`.
//
//  WHAT CHANGED (wave 25 round 3 — the rule `resolvedPadding`'s doc comment
//  states): at CAL-RC1 the ref stopped injecting a body padding at all. It
//  renders at 358 wide with `padding: 0` and the 16px frame is memcpy'd
//  around the finished PNG; an image-space translation has no cascade, so
//  the FRAME is unconditional and the author's body padding is an
//  ADDITIONAL inset inside it. Each side resolves to `frame + declared`
//  (declared defaulting to 0, clamped at 0 by CSS 2.1 §8.4): nothing
//  declared → 16, `padding: 0` → 16, `padding-left: 40px` → 56. The web
//  (ComposedCanvasPadding.test.tsx) and Compose (ComposedCanvasPaddingTest.kt)
//  twins moved to that rule in wave 25; this file kept the wave-24 rule
//  because until 2026-10-06 its bundle could not run at all, and four of
//  its pins failed with exactly the shipped arithmetic (16 / 56 / 20 / 16).
//  Wave 53 (harness-hygiene T1-b) moved them to the wave-25 contract, named
//  after the Compose twin.
//
//  The contract pinned here is the SAME one the web harness
//  (resolveCanvasPadding) and Compose (resolveComposedCanvasPadding)
//  implement, so the three composed canvases can never drift apart.
//
//  Device-free like OutOfFlowAnchorTests: `resolvedPadding` is a pure
//  function over the decoded document, never a rendered view.
//

import XCTest
// IRDocument decodes through the runtime's public Decodable witness — the
// memberwise inits are package-internal, so tests build documents from JSON
// exactly as the harness does.
import StyleConverterRuntime
@testable import StyleConverterTest

final class ComposedCanvasPaddingTests: XCTestCase {

    /// The image-space frame — capture-browser-ref.mjs's CANVAS_PAD_PX, the
    /// canvas's `Self.padding` (WPTCanvas.canvasFramePx). Every side starts here.
    private let refPad: CGFloat = 16

    /// Build a one-body-root document whose body carries `props` (raw IR
    /// property JSON), plus one plain root so the doc is non-degenerate.
    private func document(bodyProps: String) throws -> IRDocument {
        let json = """
        {"irVersion": 2, "minReaderVersion": 2, "components": [
          {"id": "b", "name": "t__body", "properties": [\(bodyProps)],
           "meta": {"role": "body-root"}},
          {"id": "r", "name": "t__0", "properties": []}
        ]}
        """
        return try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8))
    }

    /// A document with NO body-root at all — the most common shape.
    private func documentWithoutBody() throws -> IRDocument {
        let json = """
        {"irVersion": 2, "minReaderVersion": 2, "components": [
          {"id": "r", "name": "t__0", "properties": []}
        ]}
        """
        return try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8))
    }

    /// The four absolute-px padding longhands at `n` — what the converter
    /// emits for `body { padding: <n>px }`.
    private func uniformPad(_ n: Double) -> String {
        ["PaddingTop", "PaddingRight", "PaddingBottom", "PaddingLeft"]
            .map { "{\"type\": \"\($0)\", \"data\": {\"px\": \(n)}}" }
            .joined(separator: ",")
    }

    // MARK: - Defaults (byte-compat with every pre-wave-24 capture)

    func testNoBodyRootKeepsTheRefPadOnEverySide() throws {
        let canvas = ComposedCaptureCanvas(document: try documentWithoutBody())
        let pad = canvas.resolvedPadding
        XCTAssertEqual(pad.top, refPad)
        XCTAssertEqual(pad.leading, refPad)
        XCTAssertEqual(pad.bottom, refPad)
        XCTAssertEqual(pad.trailing, refPad)
    }

    func testBodyRootWithoutPaddingKeepsTheRefPad() throws {
        // The a98rgb-003 shape: a body-root exists (it declares a
        // background) but says nothing about padding — must not move.
        let doc = try document(bodyProps:
            "{\"type\": \"BackgroundColor\", \"data\": {\"srgb\": {\"r\": 0.5, \"g\": 0.5, \"b\": 0.5, \"a\": 1.0}}}")
        let pad = ComposedCaptureCanvas(document: doc).resolvedPadding
        XCTAssertEqual(pad.top, refPad)
        XCTAssertEqual(pad.leading, refPad)
    }

    // MARK: - The clip-path-circle-007 shape

    func testZeroedBodyPadKeepsTheImageFrameOnEverySide() throws {
        // `body, div { padding: 0 }` expands to the four longhands at px 0.
        // Wave 24 let that zero the canvas inset, because the frame WAS the
        // (author-beatable) injected body padding. Since wave 25 round 3 the
        // frame is applied to the ref PNG in image space, which no author
        // rule can cancel, so the canvas keeps it and adds the declared zero.
        let doc = try document(bodyProps: uniformPad(0))
        let pad = ComposedCaptureCanvas(document: doc).resolvedPadding
        XCTAssertEqual(pad.top, refPad)
        XCTAssertEqual(pad.leading, refPad)
        XCTAssertEqual(pad.bottom, refPad)
        XCTAssertEqual(pad.trailing, refPad)
    }

    // MARK: - Per-side resolution (the cascade is per-longhand)

    func testResolutionIsPerSideUndeclaredSidesKeepTheBareFrame() throws {
        // The cascade is per-LONGHAND: `body { padding-left: 40px }` leaves
        // the other three sides at the bare frame and stacks 40 INSIDE the
        // frame on the left (16 + 40 = 56) — the ref renders that 40px pad in
        // its 358-wide viewport and the image frame adds 16 around it.
        let doc = try document(bodyProps: "{\"type\": \"PaddingLeft\", \"data\": {\"px\": 40.0}}")
        let pad = ComposedCaptureCanvas(document: doc).resolvedPadding
        XCTAssertEqual(pad.leading, refPad + 40)
        XCTAssertEqual(pad.trailing, refPad)
        XCTAssertEqual(pad.top, refPad)
        XCTAssertEqual(pad.bottom, refPad)
    }

    func testPhysicalSidesMapToLeadingTrailingCorrectly() throws {
        // The WPT composed canvas is always LTR-framed, so PaddingLeft is
        // `leading` and PaddingRight is `trailing`. Asymmetric values catch
        // a swap that symmetric ones would hide; each stacks on the frame
        // (16 + 4 = 20, 16 + 9 = 25).
        let doc = try document(bodyProps:
            "{\"type\": \"PaddingLeft\", \"data\": {\"px\": 4.0}},"
            + "{\"type\": \"PaddingRight\", \"data\": {\"px\": 9.0}}")
        let pad = ComposedCaptureCanvas(document: doc).resolvedPadding
        XCTAssertEqual(pad.leading, refPad + 4)
        XCTAssertEqual(pad.trailing, refPad + 9)
    }

    // MARK: - Honest fallbacks

    func testRuntimeDependentLengthKeepsTheDefault() throws {
        // The converter emits `padding: 1%` with no absolute px. This canvas
        // has no honest answer, so the side keeps the default rather than
        // inventing a number — the documented contract, not a fallthrough.
        let doc = try document(bodyProps:
            "{\"type\": \"PaddingTop\", \"data\": {\"original\": {\"v\": 1.0, \"u\": \"PERCENT\"}}}")
        XCTAssertEqual(ComposedCaptureCanvas(document: doc).resolvedPadding.top, refPad)
    }

    func testNegativePadClampsToZeroLeavingTheBareFrame() throws {
        // CSS 2.1 §8.4 forbids negative padding; a malformed IR must never
        // pull canvas content outside the frame (and thus outside the crop).
        // The AUTHOR term clamps at 0, so the side resolves to the bare
        // frame — never inside it.
        let doc = try document(bodyProps: "{\"type\": \"PaddingTop\", \"data\": {\"px\": -8.0}}")
        XCTAssertEqual(ComposedCaptureCanvas(document: doc).resolvedPadding.top, refPad)
    }

    // MARK: - The default geometry stays byte-identical

    func testDefaultPadReproducesTheStaticViewportContentBox() throws {
        // `body` publishes a viewport built from resolvedPadding; for a
        // padding-free document it must equal the static `viewport` pin
        // (390 − 2×16 = 358) or every unaffected capture would shift.
        let pad = ComposedCaptureCanvas(document: try documentWithoutBody()).resolvedPadding
        XCTAssertEqual(ComposedCaptureCanvas.width - pad.leading - pad.trailing,
                       CGFloat(ComposedCaptureCanvas.viewport.rootContainingBlock))
    }
}
