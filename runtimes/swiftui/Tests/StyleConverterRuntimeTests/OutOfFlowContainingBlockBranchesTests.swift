//
//  OutOfFlowContainingBlockBranchesTests.swift
//  Wave 55 (lane L7, unit U6-swift, pins S2-S6 — wave-54 L4 skeptic S-X7 and K-X4's Swift twin, and the three
//  breadcrumbs: the positioned-establisher one was never asserted on Swift).
//
//  OutOfFlowContainingBlock.swift (wave 54, lane L4) is pinned on the converter's EMITTED shapes by
//  OutOfFlowContainingBlockTests P1; these pins reach the branches the corpus never carries:
//    S2 css-contain-2 §3.4 — `contain: paint` as a bare STRING (the space-separated form) establishes;
//    S3 filter-effects-1 §5 — a `filter` OBJECT leaf (`url(…)` / an unresolved expression) establishes;
//    S4-S6 no silent fallthrough — after PropertyTracker._resetForTests(), the decode-failed, implied-containment
//       and positioned-establisher breadcrumbs were each emitted: a second logOnce of the key reports "already seen".
//  S5 reads the VERBATIM component content-visibility-auto-shared-element__3__2__0-092 (tools/titan/runs/
//  wave54-final/sections/css-view-transitions/per-test-ir/wpt__css-view-transitions__content-visibility-auto-
//  shared-element.json, sha256 fae6d09f…, byte-identical at wave55-open). Compose twin: OutOfFlowContainingBlock-
//  BranchesTest.kt (C4-C7).
//
//  MUTATIONS (executed red → restore → green, sha256 in tools/titan/results/wave55-instruments/_note.md):
//    S2 `case .string: return false` · S3 the object branch of filterListIsUsed → false · S4 / S5 / S6 each logOnce
//    call deleted.
//

import XCTest
@testable import StyleConverterRuntime

final class OutOfFlowContainingBlockBranchesTests: XCTestCase {

    // One wire property from a JSON literal.
    private func prop(_ type: String, _ json: String) throws -> IRProperty {
        try JSONDecoder().decode(IRProperty.self, from: Data(#"{"type":"\#(type)","data":\#(json)}"#.utf8))
    }

    // The tracker's dedupe set is process-global: every pin starts empty.
    override func setUp() { super.setUp(); PropertyTracker._resetForTests() }

    // "Already seen" — the key was emitted by the call under test.
    private func wasLogged(_ key: String) -> Bool { !PropertyTracker.logOnce(key: key, message: "") }

    func testS2ContainPaintAsABareStringEstablishes() throws {
        XCTAssertTrue(OutOfFlowContainingBlock.establishes([try prop("Contain", #""paint""#)]))
    }

    func testS3AFilterObjectLeafEstablishes() throws {
        XCTAssertTrue(OutOfFlowContainingBlock.establishes([try prop("Filter", ##"{"url":"#f"}"##)]))
    }

    func testS4AnUndecodableContainLeafAnswersFalseAndLeavesTheDecodeBreadcrumb() throws {
        XCTAssertFalse(OutOfFlowContainingBlock.establishes([try prop("Contain", #"{"paint":{"bad":1}}"#)]))
        XCTAssertTrue(wasLogged(OutOfFlowContainingBlock.containDecodeBreadcrumb))
    }

    /// content-visibility-auto-shared-element__3__2__0-092, verbatim.
    private let cvAuto = #"{"id":"content-visibility-auto-shared-element__3__2__0-092","name":"content-visibility-auto-shared-element__3__2__0","properties":[{"type":"ContentVisibility","data":"AUTO"},{"type":"ContainIntrinsicSize","data":{"type":"length","px":500}},{"type":"Position","data":"STATIC"},{"type":"Width","data":{"type":"length","px":98}},{"type":"Height","data":{"type":"length","px":500}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"slot":{"parent":"content-visibility-auto-shared-element__3__2-091"},"text":"descendant c-v"}"#
    private struct Component: Decodable { let properties: [IRProperty] }

    func testS5ADeclaredContentVisibilityLeavesTheImpliedContainmentBreadcrumb() throws {
        let props = try JSONDecoder().decode(Component.self, from: Data(cvAuto.utf8)).properties
        // No candidate type: not an establisher, but the implied containment is named.
        XCTAssertFalse(OutOfFlowContainingBlock.establishes(props))
        XCTAssertTrue(wasLogged(OutOfFlowContainingBlock.impliedContainmentBreadcrumb))
    }

    func testS6APositionedEstablisherLeavesItsBreadcrumb() throws {
        // backdrop-filter-clip-rect's container shape: ABSOLUTE + backdrop-filter — the claim is withheld.
        XCTAssertFalse(OutOfFlowContainingBlock.establishes(
            [try prop("Position", #""ABSOLUTE""#), try prop("BackdropFilter", #"[{"fn":"blur","r":{"px":10}}]"#)]))
        XCTAssertTrue(wasLogged(OutOfFlowContainingBlock.positionedEstablisherBreadcrumb))
    }
}
