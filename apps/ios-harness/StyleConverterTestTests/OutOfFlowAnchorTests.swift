//
//  OutOfFlowAnchorTests.swift
//  StyleConverterTestTests
//
//  Device-free unit tests for the per-axis auto-inset predicates on
//  CaptureCanvas (hasHorizontalInset / hasVerticalInset) — the pure
//  decisions behind the out-of-flow branch's static-position padding:
//  an axis with NO declared inset keeps the box at its CSS static
//  position (content origin inside the 16pt canvas padding, CSS 2.1
//  §10.3.7 / §10.6.4), while a declared inset anchors at the card
//  corner (the containing block's padding edge). Same device-free
//  pattern as InboxModeTests; the rendered geometry itself is proven
//  by the visual pipeline (test-ios.sh SSIM vs web).
//

import XCTest
// IRComponent decodes through the runtime's public Decodable witness —
// the memberwise init is internal to the package, so tests build
// components from JSON exactly as the harness does.
import StyleConverterRuntime
@testable import StyleConverterTest

final class OutOfFlowAnchorTests: XCTestCase {

    /// Build a minimal component carrying only the given IR property
    /// types (payloads are irrelevant to the presence-based predicates).
    private func component(_ types: [String]) throws -> IRComponent {
        // Standalone component decode rides the tolerant dual-read
        // decoder (IRModels.swift documents this as the cheap test path).
        let props = types
            .map { "{\"type\": \"\($0)\", \"data\": \"20px\"}" }
            .joined(separator: ",")
        let json = "{\"id\": \"t\", \"name\": \"T\", \"properties\": [\(props)]}"
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    // MARK: - All-auto insets → static position on both axes

    func testAllAutoInsetsNeitherAxisAnchorsAtPaddingEdge() throws {
        // No inset properties at all — CSS keeps BOTH axes at the static
        // position, so the canvas must restore the 16pt padding on both.
        let c = try component(["Position", "Width", "Height"])
        XCTAssertFalse(CaptureCanvas.hasHorizontalInset(c))
        XCTAssertFalse(CaptureCanvas.hasVerticalInset(c))
    }

    // MARK: - Per-axis split (a declared inset anchors ONLY its own axis)

    func testLeftInsetAnchorsHorizontalAxisOnly() throws {
        // `left: 20px; top/bottom: auto` — x anchors at the card corner,
        // y stays at the static position (§10.3.7 / §10.6.4 per-axis rule).
        let c = try component(["Left"])
        XCTAssertTrue(CaptureCanvas.hasHorizontalInset(c))
        XCTAssertFalse(CaptureCanvas.hasVerticalInset(c))
    }

    func testTopInsetAnchorsVerticalAxisOnly() throws {
        // Mirror case: `top: 20px; left/right: auto`.
        let c = try component(["Top"])
        XCTAssertFalse(CaptureCanvas.hasHorizontalInset(c))
        XCTAssertTrue(CaptureCanvas.hasVerticalInset(c))
    }

    func testRightAndBottomCountAsInsetsToo() throws {
        // An axis is "declared" when EITHER side carries an inset — right
        // and bottom anchor their axes exactly like left and top.
        XCTAssertTrue(CaptureCanvas.hasHorizontalInset(try component(["Right"])))
        XCTAssertTrue(CaptureCanvas.hasVerticalInset(try component(["Bottom"])))
    }

    // MARK: - Logical insets map through the LTR horizontal-tb resolution

    func testLogicalInsetsMapToTheSameAxes() throws {
        // Logical sides resolve to physical per the runtime's
        // PositionExtractor LTR mapping: inline → horizontal, block →
        // vertical. The canvas predicate must agree with the applier.
        XCTAssertTrue(CaptureCanvas.hasHorizontalInset(try component(["InsetInlineStart"])))
        XCTAssertTrue(CaptureCanvas.hasHorizontalInset(try component(["InsetInlineEnd"])))
        XCTAssertTrue(CaptureCanvas.hasVerticalInset(try component(["InsetBlockStart"])))
        XCTAssertTrue(CaptureCanvas.hasVerticalInset(try component(["InsetBlockEnd"])))
        // And they never leak onto the OTHER axis.
        XCTAssertFalse(CaptureCanvas.hasVerticalInset(try component(["InsetInlineStart"])))
        XCTAssertFalse(CaptureCanvas.hasHorizontalInset(try component(["InsetBlockEnd"])))
    }
}
