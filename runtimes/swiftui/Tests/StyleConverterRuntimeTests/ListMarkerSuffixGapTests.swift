//
//  ListMarkerSuffixGapTests.swift
//  StyleConverterRuntimeTests — wave 55, lane L5 (U2o-ios / U2i-ios).
//
//  Pins ListMarkerSuffixGap: 0 for a marker string whose last scalar is
//  U+3001 (the space-less `cjk-decimal` family suffix, css-counter-styles-3
//  §6.1), the 4 pt stand-in for the trimmed trailing space otherwise. TWIN
//  of the Compose suite ListMarkerSuffixGapTest.kt — same rows.
//
//  Mutation EXECUTED by the lane (bracketed in tools/titan/results/
//  wave55-counter-suffix-rows/_note.md): dropping the U+3001 test (always
//  the stand-in) turns the two zero-gap pins red.
//

import XCTest
@testable import StyleConverterRuntime

final class ListMarkerSuffixGapTests: XCTestCase {

    /// The verbatim baked markers that end in U+3001: counter-suffix rows
    /// 5–6 ("一、") and css3-counter-styles-004 ("一〇、").
    func testIdeographicCommaSuffixTakesNoGap() {
        XCTAssertEqual(ListMarkerSuffixGap.points(forMarker: "一、"), 0)
        XCTAssertEqual(ListMarkerSuffixGap.points(forMarker: "一〇、"), 0)
    }

    /// Every other suffix stood for a trimmed U+0020: the stand-in stays.
    func testSpaceSuffixesKeepTheStandInGap() {
        // decimal ". " → "1."
        XCTAssertEqual(ListMarkerSuffixGap.points(forMarker: "1."), 4)
        // korean-hangul-formal ", " → "일,"
        XCTAssertEqual(ListMarkerSuffixGap.points(forMarker: "일,"), 4)
        // hebrew ". " → "א."
        XCTAssertEqual(ListMarkerSuffixGap.points(forMarker: "א."), 4)
        // No marker string: the stand-in (callers never paint one).
        XCTAssertEqual(ListMarkerSuffixGap.points(forMarker: ""), 4)
    }

    /// The stand-in IS ListMarkerRow.gapPt — one constant, one home.
    func testTheStandInIsTheSharedRowGap() {
        XCTAssertEqual(ListMarkerSuffixGap.points(forMarker: "1."), ListMarkerRow.gapPt)
    }

    /// A U+3001 that is not the LAST scalar is not the suffix.
    func testOnlyTheTailCounts() {
        XCTAssertEqual(ListMarkerSuffixGap.points(forMarker: "、1."), 4)
    }
}
