//
//  WordBreakOpportunitiesTests.swift
//  Applier campaign wave 41, lane T3.
//
//  Pin table for the in-word break-opportunity model and its integration
//  into the greedy fold — the Swift half of the twin table
//  (WordBreakOpportunitiesTest.kt pins the Kotlin side; change one,
//  change both). The measurer is injected (one character = one unit, the
//  suite's standard shape), so these pins need no registered font: they
//  pin the ARITHMETIC, with the css-text/hyphens WPT fixtures verbatim.
//

import XCTest
@testable import StyleConverterRuntime

final class WordBreakOpportunitiesTests: XCTestCase {

    /// One char = one unit, so maxWidth reads as a character count.
    private let charWidth: (String) -> CGFloat = { CGFloat($0.count) }

    // MARK: - the analyzer

    /// A mark-free word decomposes to itself with no ops — the identity
    /// that keeps the wave-40 fold decisions byte-stable.
    func testAPlainWordHasNoOps() {
        let w = WordBreakOpportunities.analyze("Deoxyribonucleic")
        XCTAssertEqual(w.text, "Deoxyribonucleic")
        XCTAssertTrue(w.ops.isEmpty)
    }

    /// css-text-3 §5.3: each U+00AD is REMOVED from the display text and
    /// recorded as a hyphenation op at its position in the cleaned word.
    func testSoftHyphensAreStrippedAndRecorded() {
        let w = WordBreakOpportunities.analyze("Deoxy\u{AD}ribo\u{AD}nucleic")
        XCTAssertEqual(w.text, "Deoxyribonucleic")
        XCTAssertEqual(w.ops.map(\.offset), [5, 9])
        XCTAssertTrue(w.ops.allSatisfy(\.paintsHyphen))
    }

    /// UAX #14 class HY/BA: a break-after op behind U+002D and U+2010;
    /// the glyph stays in the text and paints nothing extra.
    func testLiteralHyphensRecordBreakAfterOps() {
        let w = WordBreakOpportunities.analyze("imple-menta\u{2010}tion")
        XCTAssertEqual(w.text, "imple-menta\u{2010}tion")
        XCTAssertEqual(w.ops.map(\.offset), [6, 12])
        XCTAssertTrue(w.ops.allSatisfy { !$0.paintsHyphen })
    }

    /// UAX #14 forbids HY × NU (`1-2` stays together); a word-final
    /// hyphen has no tail to move; a word-initial soft hyphen has no
    /// head to break off; a shy right after a literal hyphen collapses
    /// into the literal op (one break point, nothing extra painted).
    func testDegenerateMarksProduceNoDuplicateOrUselessOps() {
        XCTAssertTrue(WordBreakOpportunities.analyze("1-2").ops.isEmpty)
        XCTAssertTrue(WordBreakOpportunities.analyze("word-").ops.isEmpty)
        XCTAssertTrue(WordBreakOpportunities.analyze("\u{AD}word").ops.isEmpty)
        let merged = WordBreakOpportunities.analyze("foo-\u{AD}bar")
        XCTAssertEqual(merged.text, "foo-bar")
        XCTAssertEqual(merged.ops.map(\.offset), [4])
        XCTAssertFalse(merged.ops[0].paintsHyphen)
    }

    // MARK: - the fold, wave-41 shapes (the wave40-final defects)

    /// hyphens-manual-013 (`Deoxy&shy;ribonucleic acid`, 10ch): the shy
    /// is taken, paints the hyphenate-character, and the mark itself
    /// never reaches the output; the unbreakable tail `ribonucleic`
    /// overflows (CSS 2.1 §9.5) exactly like the Chromium ref. At
    /// wave40-final iOS rendered this 0.8818: the fold committed TWO
    /// lines, the box height was pinned to two, and TextKit re-broke the
    /// overflowing first line at the shy it could still see.
    func testManual013FoldsToTheChromiumLines() {
        let lines = GreedyLineBreaker.lines(
            text: "Deoxy\u{AD}ribonucleic acid", maxWidth: 10,
            measure: charWidth, hyphenChar: "-")
        XCTAssertEqual(lines, ["Deoxy-", "ribonucleic", "acid"])
        // The tail is genuinely unbreakable → the §5.2 overflow claim
        // stands (the caller's fixedSize gate needs it).
        XCTAssertTrue(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            lines, maxWidth: 10, measure: charWidth))
    }

    /// hyphens-manual-011 (`Deoxy&shy;ribo&shy;nucleic acid`, 10ch): shy
    /// splits are GREEDY — the LAST point that fits wins, and
    /// `Deoxyribo-` is exactly 10ch with its painted hyphen (wave40-final
    /// iOS 0.8832 from the same two-line undercount).
    func testManual011TakesTheGreediestShy() {
        let lines = GreedyLineBreaker.lines(
            text: "Deoxy\u{AD}ribo\u{AD}nucleic acid", maxWidth: 10,
            measure: charWidth, hyphenChar: "-")
        XCTAssertEqual(lines, ["Deoxyribo-", "nucleic", "acid"])
        XCTAssertFalse(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            lines, maxWidth: 10, measure: charWidth))
    }

    /// hyphens-none-012 (`regu-lation imple-menta-tion now`, 6ch): the
    /// fold now reproduces the ref's six lines (wave40-final iOS 0.8548:
    /// three committed lines, six rendered, text centered out of the
    /// pinned two-line-short box).
    func testNone012FoldsToTheChromiumLines() {
        let lines = GreedyLineBreaker.lines(
            text: "regu-lation imple-menta-tion now", maxWidth: 6,
            measure: charWidth)
        XCTAssertEqual(lines, ["regu-", "lation", "imple-", "menta-", "tion", "now"])
    }

    /// The overflow fallback: an OPENING word none of whose heads fit
    /// still breaks at its FIRST explicit op — CSS minimizes the
    /// overflow rather than keeping the whole word (the none-012 ref
    /// wraps `imple-menta-tion` even where `imple-` alone would overflow
    /// a narrower box; on-device the natives' `ch`-narrowed boxes hit
    /// exactly this). Mid-line the fallback must NOT fire: the space
    /// break before the word is the correct earlier opportunity.
    func testTheOverflowFallbackBreaksOpeningWordsAtTheFirstOp() {
        let lines = GreedyLineBreaker.lines(
            text: "imple-menta-tion", maxWidth: 5, measure: charWidth)
        // "imple-" is 6 > 5, yet the fold still breaks there and again
        // at "menta-": both heads overflow minimally, the tail fits.
        XCTAssertEqual(lines, ["imple-", "menta-", "tion"])
        // Mid-line: the word moves down whole, then the fallback applies
        // on the fresh line — the "xx" line never absorbs a head.
        XCTAssertEqual(
            GreedyLineBreaker.lines(
                text: "xx imple-menta-tion", maxWidth: 5, measure: charWidth),
            ["xx", "imple-", "menta-", "tion"])
    }

    /// css-text-3 §5.3 PRIORITY: characters inside the word that
    /// explicitly suggest break points take priority over the
    /// hyphenation resource — the hyphens-auto-control ref breaks
    /// `fragilistic&shy;expiali` at the conditional hyphen in ALL THREE
    /// widths (12/14/16ch), ignoring the automatic points around it
    /// (wave40-final iOS 0.9496: boxes 2–3 broke at dictionary points).
    func testExplicitMarksSuppressTheDictionary() {
        // A fake dictionary offering the greedier `fragilisticex|piali`.
        let dictionary: (String) -> [Int] = { _ in [2, 6, 13, 15] }
        let lines = GreedyLineBreaker.lines(
            text: "fragilistic\u{AD}expiali", maxWidth: 14,
            measure: charWidth, hyphenate: dictionary, hyphenChar: "-")
        // The shy (offset 11) wins although offset 13 would also fit.
        XCTAssertEqual(lines, ["fragilistic-", "expiali"])
        // Control: with no explicit mark the same dictionary IS used and
        // the greedy walk takes its last fitting point — the wave-40
        // contract, unchanged for mark-free words.
        XCTAssertEqual(
            GreedyLineBreaker.lines(
                text: "fragilisticexpiali", maxWidth: 14,
                measure: charWidth, hyphenate: dictionary, hyphenChar: "-"),
            ["fragilisticex-", "piali"])
    }

    /// The wave-40 dictionary contract survives verbatim: no fitting
    /// dictionary point ⇒ the whole word overflows (no fallback for
    /// dictionary points — only EXPLICIT marks minimize overflow).
    func testDictionaryPointsNeverUseTheOverflowFallback() {
        let dictionary: (String) -> [Int] = { _ in [8] }
        let lines = GreedyLineBreaker.lines(
            text: "regulation", maxWidth: 4,
            measure: charWidth, hyphenate: dictionary, hyphenChar: "-")
        // "regulat-" (9) does not fit 4; the word stays whole.
        XCTAssertEqual(lines, ["regulation"])
    }

    /// A multi-shy word narrower than every head still walks the ops in
    /// order across MULTIPLE lines, rebasing the surviving ops into each
    /// tail (shy positions cannot be recomputed once the marks are
    /// stripped).
    func testAMultiShyWordSpansThreeLines() {
        let lines = GreedyLineBreaker.lines(
            text: "Deoxy\u{AD}ribo\u{AD}nucleic", maxWidth: 6,
            measure: charWidth, hyphenChar: "-")
        XCTAssertEqual(lines, ["Deoxy-", "ribo-", "nucleic"])
    }
}
