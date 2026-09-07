//
//  ListItemMarkerGateTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 30, lane 3 — pin table for ListItemMarkerGate (fix B1): WHICH
//  boxes generate a ::marker from their own `display`.
//
//  ## The defect these pin against
//  Both natives gated marker synthesis on the PARENT tag alone, so a
//  `<div style="display:list-item">` — the shape
//  wpt/css-lists/change-list-style-position-002 and -003 are built from —
//  painted no marker while web (which just sets `display:list-item` and
//  lets Chromium generate the box) painted one. On -003 that left the
//  inner box 20px too high (one line box) on BOTH natives: min-SSIM 0.9317
//  iOS↔web / 0.9314 Android↔web.
//
//  TWIN of the Compose suite ListItemMarkerGateTest.kt — same cases, same
//  expectations. Change one, change both.
//

import XCTest
@testable import StyleConverterRuntime

final class ListItemMarkerGateTests: XCTestCase {

    private func prop(_ type: String, _ value: String) -> IRProperty {
        IRProperty(type: type, data: .string(value))
    }

    /// The EXACT root payload of the live
    /// `wpt__css-lists__change-list-style-position-003` document: a tagless
    /// component with `Display: LIST_ITEM` and `ListStylePosition: INSIDE`
    /// and no `list-style-type` at all.
    private var outerItem: [IRProperty] {
        [prop("Display", "LIST_ITEM"), prop("ListStylePosition", "INSIDE")]
    }

    /// Its child in the same document — the one that adds `decimal`.
    private var innerItem: [IRProperty] {
        outerItem + [prop("ListStyleType", "decimal")]
    }

    // MARK: - isListItemDisplay: the css-lists-3 §3.1 predicate

    func testTheLiveWireSpellingIsRecognised() {
        XCTAssertTrue(ListItemMarkerGate.isListItemDisplay(outerItem))
    }

    func testTheCSSSpellingIsRecognisedToo() {
        // schema/spec/05-versioning.md tolerates a foreign producer's
        // unknown property DATA, so the hyphenated CSS keyword must not
        // silently fall through to "not a list item".
        XCTAssertTrue(ListItemMarkerGate.isListItemDisplay(
            [prop("Display", "list-item")]))
    }

    func testEveryOtherDisplayIsNotAListItem() {
        for keyword in ["BLOCK", "INLINE", "FLEX", "GRID", "NONE", "INLINE_BLOCK"] {
            XCTAssertFalse(ListItemMarkerGate.isListItemDisplay(
                [prop("Display", keyword)]), keyword)
        }
        // No Display entry at all — the `<ul>`/`<li>` shape of
        // change-list-style-type-001, which must keep the parent-loop path.
        XCTAssertFalse(ListItemMarkerGate.isListItemDisplay(
            [prop("ListStyleType", "square")]))
    }

    func testALaterDisplayEntryShadowsAnEarlierOne() {
        // Last-wins, the fold rule every other extractor in this package
        // uses — a dynamic bucket can append a second Display.
        XCTAssertFalse(ListItemMarkerGate.isListItemDisplay(
            [prop("Display", "LIST_ITEM"), prop("Display", "BLOCK")]))
        XCTAssertTrue(ListItemMarkerGate.isListItemDisplay(
            [prop("Display", "BLOCK"), prop("Display", "LIST_ITEM")]))
    }

    // MARK: - ownMarkerConfig / ownMarkerText

    func testASelfMarkingItemWithNoTypeDeclarationTakesTheInitialDisc() {
        // css-lists-3 §3.1: `list-style-type`'s INITIAL value is `disc`.
        // HTML §15.3.7's ul/ol rules are declarations on a CONTAINER
        // element and match no `display:list-item` div, so the initial
        // value is what stands — and web paints exactly that (a 5×5 bullet
        // at the outer item's content origin, ink rows 30–34).
        XCTAssertEqual(ListItemMarkerGate.ownMarkerConfig(outerItem).type, .disc)
        XCTAssertEqual(ListItemMarkerGate.ownMarkerText(outerItem), "\u{2022}")
    }

    func testTheItemsOwnListStyleTypeWins() {
        XCTAssertEqual(ListItemMarkerGate.ownMarkerConfig(innerItem).type, .decimal)
        // Ordinal 1 — the documented KNOWN GAP (no sibling index at this
        // call site). Both corpus documents nest their items, so 1 is the
        // right number for every one of them.
        XCTAssertEqual(ListItemMarkerGate.ownMarkerText(innerItem), "1.")
    }

    // MARK: - rendersOwnLeadingMarker: the four gates

    func testBothListItemsOfTheLivePosition003DocumentRenderAMarker() {
        XCTAssertTrue(ListItemMarkerGate.rendersOwnLeadingMarker(
            sourceTag: nil, properties: outerItem))
        XCTAssertTrue(ListItemMarkerGate.rendersOwnLeadingMarker(
            sourceTag: nil, properties: innerItem))
    }

    func testATaggedLiIsLeftToTheParentLoopPath() {
        // change-list-style-type-002 / add-inline-child-after-marker-001
        // put `Display: LIST_ITEM` on the `<li>` ITSELF. Firing here as
        // well would paint that item TWO markers.
        XCTAssertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(
            sourceTag: "li", properties: innerItem))
        XCTAssertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(
            sourceTag: "LI", properties: innerItem))
    }

    func testAnOutsideMarkerKeepsTheCurrentNoMarkerBehaviour() {
        // MEASURED gate, not an oversight: css-lists-3 §3.5 hangs an
        // `outside` marker in the item's MARGIN area, and a leading line
        // box would instead push the item's content down a line the
        // browser does not have. change-list-style-position-002 — three
        // nested `outside` list items — scores 1.0000 / 1.0000 / 0.9992
        // today with no markers at all, and its reference hangs all three
        // LEFT of the border box.
        XCTAssertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(
            sourceTag: nil,
            properties: [prop("Display", "LIST_ITEM"),
                         prop("ListStylePosition", "OUTSIDE")]))
        // …and the INITIAL value of list-style-position is `outside`
        // (css-lists-3 §3.1), so an item that declares no position at all
        // is the same case.
        XCTAssertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(
            sourceTag: nil, properties: [prop("Display", "LIST_ITEM")]))
    }

    func testListStyleTypeNoneGeneratesNoMarkerBox() {
        // css-lists-3 §3.1 — the item has NO marker, so its content must
        // start at its own content edge and no line box may be reserved.
        XCTAssertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(
            sourceTag: nil,
            properties: outerItem + [prop("ListStyleType", "none")]))
    }

    func testAPlainBlockBoxNeverRendersOne() {
        // The whole 327-pair dark stage: no Display LIST_ITEM anywhere, so
        // this gate is inert for every committed baseline.
        XCTAssertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(
            sourceTag: "div",
            properties: [prop("Display", "BLOCK"),
                         prop("ListStylePosition", "INSIDE")]))
    }
}
