//
//  AutoHyphenationTests.swift
//  StyleConverterRuntimeTests — applier campaign wave 40, lane T2.
//
//  TWIN of the Compose suite AutoHyphenationTest.kt — SAME cases, SAME
//  expectations (repo twin rule). Change one, change both.
//
//  This file pins one thing the Kotlin twin cannot: on iOS the
//  hyphenator is a plain CoreFoundation string query, so the DICTIONARY
//  ITSELF is testable without a device — `breakOffsets` is pinned below
//  against the exact words the WPT css-text/hyphens family uses.
//

import XCTest
@testable import StyleConverterRuntime

final class AutoHyphenationTests: XCTestCase {

    // MARK: - §6.1: `auto` AND a language, both required

    /// The tagged case: WPT css-text/hyphens-auto-010 (`<body lang="en">`
    /// + `hyphens: auto`), where the Chromium ref breaks `regu-/lation`.
    func testAutoWithALanguageEngages() {
        XCTAssertTrue(AutoHyphenation.engaged("AUTO", "en"))
        XCTAssertTrue(AutoHyphenation.engaged("auto", "en-us"))
    }

    /// The UNTAGGED case is the one WPT css-text/hyphens-auto-001 asserts:
    /// "automatic hyphenation must not work without language tagging".
    func testAutoWithoutALanguageDoesNotEngage() {
        XCTAssertFalse(AutoHyphenation.engaged("AUTO", nil))
        XCTAssertFalse(AutoHyphenation.engaged("AUTO", ""))
        XCTAssertFalse(AutoHyphenation.engaged("AUTO", "   "))
    }

    /// `manual` is the INITIAL value and `none` forbids breaking outright.
    func testOnlyTheAutoKeywordEngages() {
        XCTAssertFalse(AutoHyphenation.engaged("manual", "en"))
        XCTAssertFalse(AutoHyphenation.engaged("MANUAL", "en"))
        XCTAssertFalse(AutoHyphenation.engaged("none", "en"))
        XCTAssertFalse(AutoHyphenation.engaged(nil, "en"))
    }

    // MARK: - the tag handed to the platform

    func testLocaleTagIsTheAuthoredTag() {
        XCTAssertEqual(AutoHyphenation.localeTag("auto", "eN-Us"), "eN-Us")
        XCTAssertEqual(AutoHyphenation.localeTag("auto", "  en  "), "en")
    }

    func testLocaleTagIsNilWhenNotEngaged() {
        XCTAssertNil(AutoHyphenation.localeTag("manual", "en"))
        XCTAssertNil(AutoHyphenation.localeTag("auto", nil))
    }

    // MARK: - the per-line breakability half (rule B's veto)

    func testLetterRunsOfFivePlusAreHyphenatable() {
        XCTAssertTrue(AutoHyphenation.hasDictionaryOpportunity("example"))
        XCTAssertTrue(AutoHyphenation.hasDictionaryOpportunity("implementation"))
        XCTAssertTrue(AutoHyphenation.hasDictionaryOpportunity("00000 example"))
    }

    /// css-text/hyphens-punctuation-001's `00000` runs: nowhere to break,
    /// so the §5.2 overflow rule must keep claiming them.
    func testDigitRunsAndShortWordsAreNot() {
        XCTAssertFalse(AutoHyphenation.hasDictionaryOpportunity("00000"))
        XCTAssertFalse(AutoHyphenation.hasDictionaryOpportunity("1234"))
        XCTAssertFalse(AutoHyphenation.hasDictionaryOpportunity("high"))
        XCTAssertFalse(AutoHyphenation.hasDictionaryOpportunity(""))
    }

    func testTheLetterRunMustBeContiguous() {
        XCTAssertFalse(AutoHyphenation.hasDictionaryOpportunity("ab-cd-ef"))
        XCTAssertFalse(AutoHyphenation.hasDictionaryOpportunity("a1b2c3d4e5"))
    }

    // MARK: - the dictionary itself (iOS-only half)

    /// The availability probe must ANSWER, not crash, for a tag the
    /// platform has no patterns for — that answer is what routes `auto`
    /// back to `manual`'s explicit opportunities.
    func testUnknownLanguageHasNoDictionary() {
        XCTAssertNil(AutoHyphenation.locale(for: "zz-ZZ"))
    }

    /// English IS bundled on every Apple platform this runtime targets,
    /// and the words below are the ones the WPT family is built from.
    /// The pin is deliberately WEAK on the exact offsets (a future OS may
    /// ship different patterns) and STRONG on the two properties the
    /// breaker relies on: ascending order, and strictly inside the word.
    func testEnglishBreakOffsetsAreAscendingAndInterior() throws {
        let en = try XCTUnwrap(AutoHyphenation.locale(for: "en-us"),
                              "en-us hyphenation dictionary must be available")
        for word in ["highway", "example", "regulation", "implementation"] {
            let offs = AutoHyphenation.breakOffsets(in: word, locale: en)
            XCTAssertFalse(offs.isEmpty, "\(word) should hyphenate in en-us")
            XCTAssertEqual(offs, offs.sorted(), "\(word): offsets must ascend")
            XCTAssertEqual(offs.count, Set(offs).count, "\(word): no duplicates")
            for o in offs {
                XCTAssertGreaterThan(o, 0, "\(word): no leading split")
                XCTAssertLessThan(o, word.utf16.count, "\(word): no trailing split")
            }
        }
    }

    /// `highway` is the word css-text/hyphens-span-002 is built on, and
    /// its own comment names the expected point ("a hyphenation point
    /// between 'high' and 'way'"). The Chromium ref paints `high-`/`way`,
    /// so this is the one offset worth pinning exactly.
    func testHighwayBreaksBetweenHighAndWay() throws {
        let en = try XCTUnwrap(AutoHyphenation.locale(for: "en-us"))
        XCTAssertEqual(AutoHyphenation.breakOffsets(in: "highway", locale: en), [4])
    }

    /// Below §6.2's floor CF is asked nothing at all — the guard is what
    /// keeps a two-letter word out of the dictionary.
    func testShortWordsAreNeverQueried() throws {
        let en = try XCTUnwrap(AutoHyphenation.locale(for: "en-us"))
        XCTAssertEqual(AutoHyphenation.breakOffsets(in: "the", locale: en), [])
        XCTAssertEqual(AutoHyphenation.breakOffsets(in: "", locale: en), [])
    }
}
