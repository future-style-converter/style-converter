//
//  PlacementTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the iOS half of the IR v2 Slot & Placement contract:
//    1. PropertyScope — the frozen SELF/CONTAINER/ITEM classification
//       must stay byte-identical to the converter's PropertyScope.kt
//       (schema/spec/03-children.md freezes both lists).
//    2. ItemPlacementExtractor — a component's ITEM-scoped declarations
//       map into the placement union exactly as the pre-v2 renderer
//       computed them inline (CSS initial values when absent).
//

import XCTest
@testable import StyleConverterRuntime

final class PlacementTests: XCTestCase {

    // MARK: - PropertyScope mirror

    /// The converter's frozen CONTAINER list, spelled out verbatim so a
    /// drive-by edit on either side breaks this pin (parity contract).
    private static let frozenContainer: Set<String> = [
        "display",
        "flex-direction", "flex-wrap", "flex-flow",
        "justify-content", "align-items", "align-content",
        "place-content", "place-items", "justify-items",
        "gap", "row-gap", "column-gap",
        "grid-template-columns", "grid-template-rows", "grid-template-areas",
        "grid-template", "grid",
        "grid-auto-columns", "grid-auto-rows", "grid-auto-flow",
        "align-tracks", "justify-tracks", "masonry-auto-flow",
        "columns", "column-count", "column-width", "column-fill",
        "column-span", "column-rule", "column-rule-width",
        "column-rule-style", "column-rule-color",
    ]

    /// The converter's frozen ITEM list, verbatim (see above).
    private static let frozenItem: Set<String> = [
        "align-self", "justify-self", "place-self",
        "order",
        "flex-grow", "flex-shrink", "flex-basis", "flex",
        "grid-area",
        "grid-column", "grid-column-start", "grid-column-end",
        "grid-row", "grid-row-start", "grid-row-end",
        "z-index",
        "position",
        "top", "right", "bottom", "left",
        "inset", "inset-block", "inset-inline",
        "inset-block-start", "inset-block-end",
        "inset-inline-start", "inset-inline-end",
    ]

    func testScopeSetsMatchConverterFrozenLists() {
        // Set equality — additions, removals and typos all surface here.
        XCTAssertEqual(PropertyScope.containerProperties, Self.frozenContainer)
        XCTAssertEqual(PropertyScope.itemProperties, Self.frozenItem)
        // The two non-default sets must never overlap.
        XCTAssertTrue(PropertyScope.containerProperties.isDisjoint(with: PropertyScope.itemProperties))
    }

    func testScopeClassificationSamples() {
        // ITEM: child-carried parent-data.
        XCTAssertEqual(PropertyScope.of("align-self"), .item)
        XCTAssertEqual(PropertyScope.of("grid-area"), .item)
        XCTAssertEqual(PropertyScope.of("z-index"), .item)
        XCTAssertEqual(PropertyScope.of("position"), .item)
        // CONTAINER: slot layout policy, incl. the multicol family
        // (frozen decision: column-* is CONTAINER-scoped).
        XCTAssertEqual(PropertyScope.of("display"), .container)
        XCTAssertEqual(PropertyScope.of("grid-template-columns"), .container)
        XCTAssertEqual(PropertyScope.of("column-span"), .container)
        XCTAssertEqual(PropertyScope.of("column-rule-color"), .container)
        // SELF: everything unlisted, case-insensitively classified.
        XCTAssertEqual(PropertyScope.of("width"), .selfScoped)
        XCTAssertEqual(PropertyScope.of("background-color"), .selfScoped)
        XCTAssertEqual(PropertyScope.of("DISPLAY"), .container)
        // Registry hook mirrors the classification 1:1.
        XCTAssertEqual(PropertyRegistry.scope(ofCss: "order"), .item)
    }

    // MARK: - ItemPlacementExtractor

    /// Decode a property list off the wire so the test exercises the
    /// exact byte shapes the converter emits.
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    func testExtractorDefaultsAreCSSInitials() throws {
        // No ITEM declarations → CSS initial claims: auto placement,
        // grow 0 / shrink 1 / basis auto, no self-alignment, no facts.
        let p = ItemPlacementExtractor.extract(from: try props("""
        [ {"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0}}} ]
        """))
        XCTAssertEqual(p.grid.request, GridItemRequest())
        XCTAssertNil(p.grid.justifySelf)
        XCTAssertNil(p.grid.alignSelf)
        XCTAssertEqual(p.flex.grow, 0)
        XCTAssertEqual(p.flex.shrink, 1)
        XCTAssertNil(p.flex.basisPx)
        XCTAssertNil(p.flex.alignSelf)
        XCTAssertNil(p.paint.zIndex)
        XCTAssertFalse(p.explicitWidth)
        XCTAssertFalse(p.explicitHeight)
    }

    func testExtractorSizeFactsAreAxisAgnostic() throws {
        // Width/Height presence rides the placement as FACTS; the
        // consuming container resolves crossAuto against its own axis.
        let p = ItemPlacementExtractor.extract(from: try props("""
        [ {"type":"Width","data":{"type":"length","px":60.0}} ]
        """))
        XCTAssertTrue(p.explicitWidth)
        XCTAssertFalse(p.explicitHeight)
        // Logical spellings count too (InlineSize/BlockSize).
        let q = ItemPlacementExtractor.extract(from: try props("""
        [ {"type":"BlockSize","data":{"type":"length","px":20.0}} ]
        """))
        XCTAssertFalse(q.explicitWidth)
        XCTAssertTrue(q.explicitHeight)
    }

    func testExtractorGridAndFlexClaims() throws {
        // Combined ITEM declarations — both blocks populate; each
        // container kind will consume only its own (design §2.2).
        let p = ItemPlacementExtractor.extract(from: try props("""
        [ {"type":"GridColumnStart","data":{"type":"number","number":2}},
          {"type":"GridColumnEnd","data":{"type":"number","number":4}},
          {"type":"GridRowStart","data":{"type":"span","count":2}},
          {"type":"AlignSelf","data":"CENTER"},
          {"type":"JustifySelf","data":"END"},
          {"type":"FlexGrow","data":{"normalizedValue":2.0}},
          {"type":"FlexShrink","data":{"normalizedValue":0.0}},
          {"type":"FlexBasis","data":{"normalizedPixels":80.0}},
          {"type":"ZIndex","data":{"value":5}} ]
        """))
        // Grid block: 1-based lines + row span keyword.
        XCTAssertEqual(p.grid.request.colStart, 2)
        XCTAssertEqual(p.grid.request.colEnd, 4)
        XCTAssertEqual(p.grid.request.rowSpan, 2)
        XCTAssertEqual(p.grid.alignSelf, .center)
        XCTAssertEqual(p.grid.justifySelf, .end)
        // Flex block mirrors the same align-self (CSS: one property).
        XCTAssertEqual(p.flex.grow, 2)
        XCTAssertEqual(p.flex.shrink, 0)
        XCTAssertEqual(p.flex.basisPx, 80)
        XCTAssertEqual(p.flex.alignSelf, .center)
        // Paint block.
        XCTAssertEqual(p.paint.zIndex, 5)
    }
}
