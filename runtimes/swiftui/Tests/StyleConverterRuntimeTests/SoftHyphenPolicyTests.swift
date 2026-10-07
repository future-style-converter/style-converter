//
//  SoftHyphenPolicyTests.swift
//  Wave 37, lane W7 — the PURE half of rules A and B.
//
//  Byte-parallel twin of the Compose lane's SoftHyphenPolicyTest: the
//  same cases against the same rules (css-text-3 §5.3 soft-hyphen
//  suppression under `hyphens: none`; css-text-3 §5.5 / CSS 2.1 §9.5
//  unbreakable-word overflow), so a divergence between the two runtimes
//  surfaces here instead of in a device screenshot.
//
//  The RASTER truth (does the label actually stop emergency-breaking
//  through ComponentRenderer?) is the wave gate's job — this file only
//  claims the arithmetic and the string rewrite.
//

import XCTest
@testable import StyleConverterRuntime

final class SoftHyphenPolicyTests: XCTestCase {

    // The literal the WPT css-text/hyphens family is authored with:
    // `Deoxy&shy;ribo&shy;nucleic acid`, the extended form of "DNA".
    private let shyWord = "Deoxy\u{00AD}ribo\u{00AD}nucleic acid"

    // MARK: - §5.3 keyword gate

    /// Only `none` suppresses. `manual` is the INITIAL value and must
    /// honour explicit opportunities, `auto` adds dictionary ones on top
    /// of manual's — neither may delete the character.
    func testOnlyNoneSuppresses() {
        XCTAssertTrue(SoftHyphenPolicy.suppresses("none"))
        XCTAssertFalse(SoftHyphenPolicy.suppresses("manual"))
        XCTAssertFalse(SoftHyphenPolicy.suppresses("auto"))
    }

    /// The wire is authored upper-case (`{"type":"Hyphens","data":"NONE"}`)
    /// while HyphensExtractor lowercases — both spellings must answer the
    /// same, or the policy would fire on exactly one of the two lanes.
    func testKeywordMatchIsCaseInsensitive() {
        XCTAssertTrue(SoftHyphenPolicy.suppresses("NONE"))
        XCTAssertTrue(SoftHyphenPolicy.suppresses("None"))
    }

    /// Absent / unrecognised keyword → the initial value `manual`, which
    /// honours soft hyphens. Answering true here would delete conditional
    /// characters from every undeclared run in the corpus.
    func testUnknownAndNilKeywordsDoNotSuppress() {
        XCTAssertFalse(SoftHyphenPolicy.suppresses(nil))
        XCTAssertFalse(SoftHyphenPolicy.suppresses(""))
        XCTAssertFalse(SoftHyphenPolicy.suppresses("break-all"))
    }

    /// Only `auto` asks for a hyphenation dictionary — the predicate
    /// behind the once-per-process wall breadcrumb. It must NOT fire for
    /// `manual`/`none`, or every text document would log the wall.
    func testOnlyAutoWantsDictionaryHyphenation() {
        XCTAssertTrue(SoftHyphenPolicy.wantsDictionaryHyphenation("auto"))
        XCTAssertTrue(SoftHyphenPolicy.wantsDictionaryHyphenation("AUTO"))
        XCTAssertFalse(SoftHyphenPolicy.wantsDictionaryHyphenation("manual"))
        XCTAssertFalse(SoftHyphenPolicy.wantsDictionaryHyphenation("none"))
        XCTAssertFalse(SoftHyphenPolicy.wantsDictionaryHyphenation(nil))
    }

    // MARK: - §5.3 string rewrite

    /// Under `none` the conditional characters go, and NOTHING else does:
    /// the visible glyph sequence is byte-identical to the ref's
    /// "Deoxyribonucleic acid".
    func testNoneDeletesOnlyTheSoftHyphens() {
        XCTAssertEqual(SoftHyphenPolicy.displayString(shyWord, mode: "none"),
                       "Deoxyribonucleic acid")
    }

    /// Under `manual` the string is untouched — the platform's own
    /// U+00AD handling IS the correct §5.3 behaviour there.
    func testManualAndAutoLeaveTheStringIntact() {
        XCTAssertEqual(SoftHyphenPolicy.displayString(shyWord, mode: "manual"), shyWord)
        XCTAssertEqual(SoftHyphenPolicy.displayString(shyWord, mode: "auto"), shyWord)
        XCTAssertEqual(SoftHyphenPolicy.displayString(shyWord, mode: nil), shyWord)
    }

    /// A run with no soft hyphen is identity under EVERY mode — the
    /// property that keeps the whole committed baseline corpus stable.
    func testRunWithoutSoftHyphenIsIdentityUnderNone() {
        XCTAssertEqual(SoftHyphenPolicy.displayString("Deoxyribonucleic acid",
                                                      mode: "none"),
                       "Deoxyribonucleic acid")
    }

    /// U+200B ZERO WIDTH SPACE is a plain break opportunity, not a
    /// hyphenation one: `hyphens` does not govern it (css-text-3 §5.3
    /// speaks only of hyphenation opportunities), so `none` must leave
    /// it in place.
    func testZeroWidthSpaceSurvivesNone() {
        let zwsp = "foo\u{200B}bar"
        XCTAssertEqual(SoftHyphenPolicy.displayString(zwsp, mode: "none"), zwsp)
    }

    // MARK: - §5.2 unbreakable-word overflow (rule B probe)

    /// One char = one unit measurer, the same shape GreedyLineBreaker's
    /// own pins use (no registered font needed).
    private let charWidth: (String) -> CGFloat = { CGFloat($0.count) }

    /// The hyphens-none-011 geometry in "characters": a 10ch box, the
    /// 16-character word "Deoxyribonucleic" then "acid". The greedy
    /// breaker commits the overlong word alone (correct — §5.2 says it
    /// overflows), and the probe must SEE that overflow so the caller can
    /// take the run out of the width-constrained layout.
    func testOverlongWordIsReportedAsOverflowing() {
        let lines = GreedyLineBreaker.lines(text: "Deoxyribonucleic acid",
                                            maxWidth: 10,
                                            measure: charWidth)
        XCTAssertEqual(lines, ["Deoxyribonucleic", "acid"])
        XCTAssertTrue(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            lines, maxWidth: 10, measure: charWidth))
    }

    /// The css-text/hyphens-none-012 shape: a "word" carrying a REAL
    /// U+002D hyphen-minus. UAX #14 class HY is a break opportunity
    /// `hyphens` does not govern; since wave 41 the fold takes it ITSELF
    /// (see WordBreakOpportunitiesTests for the full model) so the line
    /// count downstream box heights are pinned from is exact — the
    /// wave40-final iOS captures of none-012/013 show the undercount
    /// (3 committed lines, 6 rendered, text centered out of the border).
    /// With both halves fitting, the probe has nothing to claim.
    func testAHyphenMinusWordIsSplitByTheFold() {
        let lines = GreedyLineBreaker.lines(text: "regu-lation", maxWidth: 6,
                                            measure: charWidth)
        XCTAssertEqual(lines, ["regu-", "lation"])
        XCTAssertFalse(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            lines, maxWidth: 6, measure: charWidth))
    }

    /// The VETO still exists for opportunity classes the fold does NOT
    /// model (en/em dashes, ZWSP, ideographs): such a line stays whole,
    /// overflows, and must not claim rule B — TextKit owns those breaks
    /// exactly as before this wave.
    func testAnUnmodeledDashKeepsTheVeto() {
        let text = "regu\u{2013}lation" // EN DASH — not an analyzer op.
        let lines = GreedyLineBreaker.lines(text: text, maxWidth: 6,
                                            measure: charWidth)
        XCTAssertEqual(lines, [text])                   // left whole…
        XCTAssertTrue(charWidth(text) > 6)              // …and overflowing
        XCTAssertFalse(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            lines, maxWidth: 6, measure: charWidth))
    }

    /// Same veto for a soft hyphen surviving into the string — i.e. the
    /// `manual`/`auto` modes, where U+00AD IS the author's declared break
    /// opportunity and TextKit taking it is correct.
    func testOverflowingLineWithASoftHyphenIsNotUnbreakable() {
        let word = "Deoxy\u{00AD}ribo\u{00AD}nucleic"
        XCTAssertFalse(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            [word], maxWidth: 6, measure: charWidth))
    }

    /// A run whose every word fits reports NO overflow — this is the
    /// negative that keeps `.fixedSize(horizontal:)` off for the corpus's
    /// ordinary prose (a false positive there would stop wrapping
    /// entirely on runs that must wrap).
    func testAllFittingLinesReportNoOverflow() {
        let lines = GreedyLineBreaker.lines(text: "one two three four",
                                            maxWidth: 10,
                                            measure: charWidth)
        XCTAssertEqual(lines, ["one two", "three four"])
        XCTAssertFalse(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            lines, maxWidth: 10, measure: charWidth))
    }

    /// A line measuring EXACTLY the wrap width is not overflow, and the
    /// half-point tolerance absorbs the fit-test-vs-proposal rounding
    /// without admitting a genuinely overlong line.
    func testExactFitAndSubPointSlackAreNotOverflow() {
        XCTAssertFalse(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            ["0123456789"], maxWidth: 10, measure: charWidth))
        XCTAssertFalse(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            ["x"], maxWidth: 0.6, measure: { _ in 1.0 }))
        XCTAssertTrue(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            ["x"], maxWidth: 0.4, measure: { _ in 1.0 }))
    }

    // MARK: - the two rules composed

    /// The end-to-end shape of hyphens-none-011: strip first, THEN break.
    /// Stripping matters for the break itself — with the soft hyphens
    /// still in the string the platform breakers take them, which is the
    /// three-line render the wave-36 gate captured.
    func testNoneThenGreedyReproducesTheChromiumLineSet() {
        let display = SoftHyphenPolicy.displayString(shyWord, mode: "none")
        let lines = GreedyLineBreaker.lines(text: display, maxWidth: 10,
                                            measure: charWidth)
        XCTAssertEqual(lines, ["Deoxyribonucleic", "acid"])
    }

    // MARK: - Wave 53 (lane L2, F1-iOS) — admitsPreBreak
    //
    // The label's pre-break precondition (ComponentRenderer.swift seam-1).
    // Strings VERBATIM off tools/titan/runs/wave52-ship/sections/css-text/
    // per-test-ir (byte-identical to wave53-open): hyphens-span-001 box 1
    // (`…hyphens-span-001__1-301`, `text` = high U+00AD way — also the six
    // hyphens-out-of-flow-001 host mirrors the iOS leading label reads) and
    // hyphens-vertical-001 box 1 (`…hyphens-vertical-001__2-320`, VERTICAL_RL).
    // MUTATION PROOF: tools/titan/results/wave53-soft-hyphen/_note.md §3
    // (S-shy: the U+00AD clause dropped; S-d3: the horizontal guard dropped).

    /// The measured defect: a space-less `manual` run with no language was
    /// refused, so TextKit broke it at the soft hyphen while the line-box
    /// pin counted ONE line (26 px boxes vs the ref's 46). Admitted now.
    func testASpacelessSoftHyphenRunIsAdmitted() {
        XCTAssertTrue(SoftHyphenPolicy.admitsPreBreak("high\u{AD}way", dictionary: false, horizontal: true))
    }

    /// A space-less run with NO soft hyphen stays refused — wave 21's
    /// whole-run gate keeps it (hyphens-manual-010's overflowing word).
    func testASpacelessRunWithoutASoftHyphenIsStillRefused() {
        XCTAssertFalse(SoftHyphenPolicy.admitsPreBreak("Deoxyribonucleic", dictionary: false, horizontal: true))
    }

    /// The pre-wave-53 precondition, verbatim: a space, or a dictionary —
    /// in EITHER writing mode (the D3 gate covers only the new clause).
    func testThePreWave53ClausesAreUnchanged() {
        XCTAssertTrue(SoftHyphenPolicy.admitsPreBreak("Deoxyribonucleic acid", dictionary: false, horizontal: true))
        XCTAssertTrue(SoftHyphenPolicy.admitsPreBreak("Deoxyribonucleic acid", dictionary: false, horizontal: false))
        XCTAssertTrue(SoftHyphenPolicy.admitsPreBreak("highway", dictionary: true, horizontal: true))
        XCTAssertTrue(SoftHyphenPolicy.admitsPreBreak("highway", dictionary: true, horizontal: false))
    }

    /// PLAN §9 D3: hyphens-vertical-001's space-less soft-hyphen run is NOT
    /// admitted — the label's wrap width is the physical width, the wrong
    /// axis for a vertical line — so its capture stays byte-identical.
    func testAVerticalSpacelessSoftHyphenRunIsRefused() {
        XCTAssertFalse(SoftHyphenPolicy.admitsPreBreak("hyphen\u{AD}ation", dictionary: false, horizontal: false))
    }

    /// The must-not-move control hyphens-auto-control (`fragilistic<U+00AD>
    /// expiali`, `hyphens: auto`, lang `en-us`, VERBATIM `…auto-control__0-235`)
    /// was ALREADY admitted before wave 53 — by the dictionary arm — so the new
    /// clause cannot change it. That rests on `en-us` having a CF hyphenation
    /// dictionary (`hyphenLocale != nil` at the seam); pinned here on Catalyst,
    /// the nearest platform this suite can run (the iOS simulator's own CF is
    /// the residual the closing gate's control-check reads).
    func testTheAutoControlRunIsAdmittedByItsDictionaryAsBefore() {
        let tag = AutoHyphenation.localeTag("AUTO", "en-us")
        XCTAssertEqual(tag, "en-us")
        XCTAssertNotNil(tag.flatMap { AutoHyphenation.locale(for: $0) })
        XCTAssertTrue(SoftHyphenPolicy.admitsPreBreak("fragilistic\u{AD}expiali", dictionary: true, horizontal: true))
    }
}
