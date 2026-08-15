//
//  ListOrdinalTests.swift
//  Wave 43, lane V4 — pin table for ListOrdinal: the HTML §4.4.5/§4.4.8
//  ordinal algorithm (`<ol start>` base, `<li value>` reset, reversed
//  countdown), built from the VERBATIM `meta.attrs` payloads the wave42
//  gate recorded for wpt/css-lists/counter-list-item.html
//  (tools/titan/runs/wave42-final/sections/css-lists/per-test-ir/
//  wpt__css-lists__counter-list-item.json: `{"start":"30"}` on the second
//  ol, `{"value":"30"}`/`{"value":"35"}` on the value'd items) and the
//  ordinals visible in the frozen Chromium ref PNG.
//
//  TWIN of the Compose suite ListOrdinalTest.kt — same cases, same
//  expectations. Change one, change both.
//

import XCTest
@testable import StyleConverterRuntime

final class ListOrdinalTests: XCTestCase {

    /// One `<li>` child, optionally carrying the wire's verbatim value.
    private func li(_ value: String? = nil) -> IRComponent {
        IRComponent(id: "li", name: "li", properties: [],
                    selectors: nil, media: nil, children: nil, slot: nil,
                    text: nil, pseudos: nil,
                    meta: IRMeta(sourceTag: "li",
                                 attrs: value.map { IRAttrs(value: $0) }))
    }

    /// A non-item sibling (an anonymous text run) — consumes no ordinal.
    private func textRun() -> IRComponent {
        IRComponent(id: "t", name: "t", properties: [],
                    selectors: nil, media: nil, children: nil, slot: nil,
                    text: "x", pseudos: nil, meta: nil)
    }

    // ── parseHtmlInteger: HTML §2.3.4.1 ────────────────────────────────

    func testPlainDigitsParse() {
        XCTAssertEqual(ListOrdinal.parseHtmlInteger("30"), 30)
    }

    func testLeadingWhitespaceAndSignsFollowTheSpec() {
        // Step 4 skips ASCII whitespace before the sign.
        XCTAssertEqual(ListOrdinal.parseHtmlInteger("  +5"), 5)
        // '-' flips the sign (steps 5-6).
        XCTAssertEqual(ListOrdinal.parseHtmlInteger("-2"), -2)
    }

    func testTrailingJunkIsIgnoredNotFatal() {
        // §2.3.4.1 returns once the digit run ends — browser behaviour.
        XCTAssertEqual(ListOrdinal.parseHtmlInteger("30abc"), 30)
    }

    func testNonIntegersAreNil() {
        XCTAssertNil(ListOrdinal.parseHtmlInteger(nil))
        XCTAssertNil(ListOrdinal.parseHtmlInteger(""))
        XCTAssertNil(ListOrdinal.parseHtmlInteger("abc"))
        // A sign with no digits is an error, not zero (step 8).
        XCTAssertNil(ListOrdinal.parseHtmlInteger("-"))
    }

    // ── ordinals: the counter-list-item payloads, forward lists ────────

    func testPlainOlNumbersFromOne() {
        // First ordered list in the fixture: no attrs at all → 1,2,3.
        XCTAssertEqual(
            ListOrdinal.ordinals(startAttr: nil, reversed: false,
                                 children: [li(), li(), li()]),
            [1, 2, 3])
    }

    func testOlStart30NumbersFromThirty() {
        // The fixture's second ol: meta.attrs {"start":"30"} — the ref
        // paints 30. 31. 32. where the raw index painted 1. 2. 3.
        XCTAssertEqual(
            ListOrdinal.ordinals(startAttr: "30", reversed: false,
                                 children: [li(), li(), li()]),
            [30, 31, 32])
    }

    func testLiValueResetsTheRunningCounter() {
        // The fixture's third ol: value=30 on item 3, value=35 on item 5
        // → ref paints 1,2,30,31,35,36.
        XCTAssertEqual(
            ListOrdinal.ordinals(
                startAttr: nil, reversed: false,
                children: [li(), li(), li("30"), li(), li("35"), li()]),
            [1, 2, 30, 31, 35, 36])
    }

    func testNegativeStartCountsUpThroughZero() {
        // HTML integers are signed: <ol start="-2"> → -2,-1,0,1.
        XCTAssertEqual(
            ListOrdinal.ordinals(startAttr: "-2", reversed: false,
                                 children: [li(), li(), li(), li()]),
            [-2, -1, 0, 1])
    }

    func testNonItemSiblingsConsumeNoOrdinal() {
        // A whitespace run between items must not shift the numbering
        // (the raw-loop-index defect this lane closes).
        let plan = ListOrdinal.ordinals(startAttr: nil, reversed: false,
                                        children: [li(), textRun(), li()])
        XCTAssertEqual([plan[0], plan[2]], [1, 2])
    }

    // ── ordinals: the reversed columns ─────────────────────────────────
    // css-lists-3 §4.4.2 "Instantiating Counters" — the SPEC rule for a
    // reversed counter created without an explicit value, not a fit to the
    // ref PNG (the ref agrees because Chromium runs the same algorithm).
    // The producer does not forward `<ol reversed>` yet, so these columns
    // are unit-only for now — the wire gap ListOrdinal's header documents.

    func testReversedWithoutStartCountsDownToOne() {
        // Fixture's first reversed ol → ref paints 3. 2. 1. §4.4.2 with no
        // `counter-set` in the walk: num = 1+1+1 (+1 trailing) = 4, and the
        // first item's own −1 step lands its marker on 3.
        XCTAssertEqual(
            ListOrdinal.ordinals(startAttr: nil, reversed: true,
                                 children: [li(), li(), li()]),
            [3, 2, 1])
    }

    func testReversedWithStart30CountsDownFromIt() {
        // Fixture's second reversed ol (start="30") → ref: 30. 29. 28.
        XCTAssertEqual(
            ListOrdinal.ordinals(startAttr: "30", reversed: true,
                                 children: [li(), li(), li()]),
            [30, 29, 28])
    }

    func testReversedAnchorsItsCountdownOnTheFirstLiValue() {
        // Fixture's third reversed ol → ref paints 32,31,30,29,35,34, and
        // css-lists-3 §4.4.2 computes exactly that: the instantiation walk
        // adds +1 for each of the two plain items, hits `counter-set: 30`
        // (`<li value="30">`) and stops ⇒ num = 32, +1 trailing ⇒ initial
        // 33; the first item's −1 step makes its marker 32, so the run
        // flows INTO the value'd item. value=35 re-sets mid-walk and the
        // countdown continues. NOT a ref-fit: the naive "initial = item
        // count" shortcut ignores `counter-set` and would open this list on
        // 6,5,… — visibly wrong, and not what any CSS UA does.
        XCTAssertEqual(
            ListOrdinal.ordinals(
                startAttr: nil, reversed: true,
                children: [li(), li(), li("30"), li(), li("35"), li()]),
            [32, 31, 30, 29, 35, 34])
    }

    // ── markerIndex: the marker(index:config:) bridge ──────────────────

    func testMarkerIndexBridgesOrdinalsToMarkersZeroBasedInput() {
        // Ordinal 30 → index 29 → decimal renders "30." end to end.
        let plan = ListOrdinal.ordinals(startAttr: "30", reversed: false,
                                        children: [li(), li(), li()])
        XCTAssertEqual(
            ListMarkerText.marker(index: ListOrdinal.markerIndex(plan, 0),
                                  config: ListMarkerConfig(type: .decimal)),
            "30.")
    }

    func testMarkerIndexWithoutAPlanIsTheRawIndex() {
        // Nil plan (non-list parents) keeps pre-wave-43 behaviour bit for
        // bit: the raw child position passes straight through.
        XCTAssertEqual(ListOrdinal.markerIndex(nil, 3), 3)
    }
}
