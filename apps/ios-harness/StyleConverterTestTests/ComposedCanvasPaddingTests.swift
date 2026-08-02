//
//  ComposedCanvasPaddingTests.swift
//  StyleConverterTestTests
//
//  wave-24 B-RC5 — device-free unit pins for
//  ComposedCaptureCanvas.resolvedPadding, the composed canvas's per-side
//  pad resolver.
//
//  WHY it exists: capture-browser-ref.mjs frames every reference page with
//  a ZERO-specificity `:where(body) { padding: 16px }`. Per CSS Selectors
//  L4 §17 `:where()` contributes no specificity, so a ref declaring its OWN
//  `body { padding: 0 }` (0,0,1) WINS and renders with no body pad. The
//  composed canvas hardcoded `Self.padding`, so every such test's whole
//  render sat (+16,+16) off its ref — MEASURED as the ENTIRE divergence of
//  css-masking/clip-path-circle-007, whose test AND ref both open with
//  `body, div { padding: 0; margin: 0 }`.
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

    /// The pipeline default — capture-browser-ref.mjs's CANVAS_PAD_PX.
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

    // MARK: - The clip-path-circle-007 fix

    func testZeroedBodyPadLandsOnEverySide() throws {
        let doc = try document(bodyProps: uniformPad(0))
        let pad = ComposedCaptureCanvas(document: doc).resolvedPadding
        XCTAssertEqual(pad.top, 0)
        XCTAssertEqual(pad.leading, 0)
        XCTAssertEqual(pad.bottom, 0)
        XCTAssertEqual(pad.trailing, 0)
    }

    // MARK: - Per-side resolution (the cascade is per-longhand)

    func testUndeclaredSidesKeepTheDefault() throws {
        // `body { padding-left: 40px }` leaves the injected `:where(body)`
        // 16px standing on the other three sides.
        let doc = try document(bodyProps: "{\"type\": \"PaddingLeft\", \"data\": {\"px\": 40.0}}")
        let pad = ComposedCaptureCanvas(document: doc).resolvedPadding
        XCTAssertEqual(pad.leading, 40)
        XCTAssertEqual(pad.trailing, refPad)
        XCTAssertEqual(pad.top, refPad)
        XCTAssertEqual(pad.bottom, refPad)
    }

    func testPhysicalSidesMapToLeadingTrailingCorrectly() throws {
        // The WPT composed canvas is always LTR-framed, so PaddingLeft is
        // `leading` and PaddingRight is `trailing`. Asymmetric values catch
        // a swap that symmetric ones would hide.
        let doc = try document(bodyProps:
            "{\"type\": \"PaddingLeft\", \"data\": {\"px\": 4.0}},"
            + "{\"type\": \"PaddingRight\", \"data\": {\"px\": 9.0}}")
        let pad = ComposedCaptureCanvas(document: doc).resolvedPadding
        XCTAssertEqual(pad.leading, 4)
        XCTAssertEqual(pad.trailing, 9)
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

    func testNegativePadClampsToZero() throws {
        // CSS 2.1 §8.4 forbids negative padding; a malformed IR must never
        // pull canvas content outside the frame (and thus outside the crop).
        let doc = try document(bodyProps: "{\"type\": \"PaddingTop\", \"data\": {\"px\": -8.0}}")
        XCTAssertEqual(ComposedCaptureCanvas(document: doc).resolvedPadding.top, 0)
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
