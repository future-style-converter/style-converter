import XCTest
@testable import StyleConverterRuntime

/// Wave 52 (lane L9, F4) — the drawn-marker CLAMP over the greedy fold
/// (GreedyLineBreaker.swift `clampLines` / `lines(clamp:)`). Twin of the
/// Kotlin GreedyLineBreakerTest's wave-52 block: same strings, same uniform
/// 19.2px-per-glyph monospace measurer, same expected lines — change one,
/// change both. The wave-41 fold pins (IOSTextLaneTests, SoftHyphenPolicy-,
/// WordBreakOpportunitiesTests) are untouched: `lines(clamp: nil)` is that
/// fold byte for byte.
///
/// MUTATION PROOF (executed 2026-10-05, mutate.py on an isolated HEAD + lane
/// + seam-2 copy, mutations-swift.result.json, sha256-verified restores):
/// S8 fit loop off → the drops / custom-marker / 025 pins fail; S9 trim
/// skipped → those plus the fitting-last-line and hard-break pins fail.
/// Fix pass: S8 re-cut on the clampHead walk as W5 (fit test forced true) —
/// the same three plus the 030/028 pins fail (mutations-m1-swift).
final class GreedyLineBreakerTests: XCTestCase {

    /// One monospace glyph advance; the strings below are measured as
    /// character counts × this.
    private let ch: CGFloat = 19.2
    private lazy var mono: (String) -> CGFloat = { [ch] s in CGFloat(s.count) * ch }

    /// css-overflow/line-clamp/block-ellipsis-025's text, VERBATIM off
    /// wave51-fix/sections/css-overflow/per-test-ir (the ONE component:
    /// `line-clamp: 4`, `width: 32.5ch`, monospace).
    private let ellipsis025 = "Test passes if there are 4 lines and the last one only contains … "
        + "supercalifragilisticexpialidocious supercalifragilisticexpialidocious Test fails: this should not be visible"

    /// block-ellipsis-025, the ref's picture: line 3 is the 34ch word
    /// overflowing the 32.5ch box (CSS 2.1 §9.5), line 4 is the marker
    /// ALONE — the second copy of the word cannot fit beside "…" either, so
    /// css-overflow-4 §4.2 hides the whole word at its soft wrap
    /// opportunity and the ellipsis is all that remains. SwiftUI's tail
    /// truncation appended "…" AFTER the full word (ios f 0.9495).
    func testClampTrimsBlockEllipsis025ToFourLinesWithTheMarkerAlone() {
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: ellipsis025, maxWidth: 32.5 * ch, measure: mono,
                                    clamp: GreedyLineBreaker.Clamp(lines: 4)),
            [
                "Test passes if there are 4 lines",
                "and the last one only contains …",
                "supercalifragilisticexpialidocious",
                "…",
            ])
    }

    /// The marker rides the last kept line when it fits — no word dropped.
    func testClampAppendsTheMarkerToAFittingLastLine() {
        // "aa bb" / "cc dd" / "ee ff" at 6ch; clamp 2 → "cc dd…" is 6ch, fits.
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "aa bb cc dd ee ff", maxWidth: 6 * ch, measure: mono,
                                    clamp: GreedyLineBreaker.Clamp(lines: 2)),
            ["aa bb", "cc dd…"])
    }

    /// css-overflow-4 §4.2: content is hidden at SOFT WRAP OPPORTUNITIES
    /// (whole words), never mid-word, until the ellipsis fits.
    func testClampDropsTrailingWordsUntilTheMarkerFits() {
        // "aa bb" / "cc dd" / "ee" at 5ch; clamp 2 → "cc dd…" is 6ch (no
        // fit) → drop "dd" → "cc…" fits.
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "aa bb cc dd ee", maxWidth: 5 * ch, measure: mono,
                                    clamp: GreedyLineBreaker.Clamp(lines: 2)),
            ["aa bb", "cc…"])
    }

    /// A clamp only ever REMOVES lines: a paragraph inside the cap comes
    /// back unchanged, and so does a non-positive cap; `clamp: nil` is the
    /// wave-41 fold byte for byte.
    func testClampIsIdentityWhenTheParagraphFitsTheCap() {
        let lines = ["aa bb", "cc"]
        XCTAssertEqual(GreedyLineBreaker.clampLines(lines, clamp: .init(lines: 2), maxWidth: 6 * ch, measure: mono), lines)
        XCTAssertEqual(GreedyLineBreaker.clampLines(lines, clamp: .init(lines: 3), maxWidth: 6 * ch, measure: mono), lines)
        XCTAssertEqual(GreedyLineBreaker.clampLines(lines, clamp: .init(lines: 0), maxWidth: 6 * ch, measure: mono), lines)
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "aa bb cc dd ee", maxWidth: 5 * ch, measure: mono),
            GreedyLineBreaker.lines(text: "aa bb cc dd ee", maxWidth: 5 * ch, measure: mono, clamp: nil))
    }

    /// The marker itself is configurable (css-overflow-4 §4.2 lets
    /// `block-ellipsis: <string>` name one) and is measured like any glyph.
    func testClampMeasuresACustomMarkerAsARealGlyphRun() {
        // "cc dd" + "[more]" = 11ch > 5 → drop "dd" → "cc[more]" = 8 > 5 →
        // drop "cc" → the marker alone.
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "aa bb cc dd ee", maxWidth: 5 * ch, measure: mono,
                                    clamp: GreedyLineBreaker.Clamp(lines: 2, marker: "[more]")),
            ["aa bb", "[more]"])
    }

    /// Hard newlines inside the paragraph count as lines too: the cap is
    /// over LINE BOXES (css-overflow-4 §5 `max-lines`), whatever made them.
    func testClampCountsHardBrokenLinesTowardTheCap() {
        // "Line 1" / "Line 2" / "Line 3" / "Line 4" (block-ellipsis-002's
        // `<br>`-separated shape), clamp 3 → the third line carries the
        // marker: "Line 3…" is 7ch inside a 10ch box.
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "Line 1\nLine 2\nLine 3\nLine 4", maxWidth: 10 * ch, measure: mono,
                                    clamp: GreedyLineBreaker.Clamp(lines: 3)),
            ["Line 1", "Line 2", "Line 3…"])
    }

    // MARK: - Wave 52 fix pass (skeptic M1): every modelled opportunity
    // MUTATION PROOF (executed 2026-10-05, mutate.py on the isolated Catalyst
    // copy — mutations-m1-swift.result.json; sha256-verified restores): W1
    // separators narrowed to U+0020 → the 030 pin fails; W2 markedLine off →
    // the 028 pin; W3 the ID/SA decline off → the decline pin; W4 the dash
    // break-after arm off → the dash pin.

    /// css-overflow/line-clamp/block-ellipsis-030 box 1, VERBATIM off
    /// wave51-fix per-test-ir: `123<U+1680>5 789`, `line-clamp: 1`, 5ch,
    /// monospace. U+1680 OGHAM SPACE MARK is UAX #14 BA, so css-overflow-4
    /// §4.2 hides `5` there: the ref paints `123…`; the U+0020-only cut
    /// baked `…` alone (skeptic repro 7). 029 (`123 5 789`, 5.1ch) rides along.
    func testClampHidesAtTheOghamSpaceMarkOfBlockEllipsis030() {
        // The wire's own declaration decides the clamp (seam-2's reader).
        let clamp = GreedyLineBreaker.drawnClamp(limit: 1, properties: [IRProperty(
            type: "LineClamp", data: .object(["type": .string("lines"), "count": .int(1)]))])
        XCTAssertEqual(clamp?.lines, 1)
        // 030 box 1, then the 029 control — both the ref's `123…`.
        XCTAssertEqual(GreedyLineBreaker.lines(text: "123\u{1680}5 789", maxWidth: 5 * ch,
                                               measure: mono, clamp: clamp), ["123\u{2026}"])
        XCTAssertEqual(GreedyLineBreaker.lines(text: "123 5 789", maxWidth: 5.1 * ch,
                                               measure: mono, clamp: clamp), ["123\u{2026}"])
    }

    /// css-overflow/line-clamp/block-ellipsis-028, VERBATIM (`line-clamp: 2`,
    /// 63.1ch, `hyphens: manual`). Line 2 is exactly 63ch; both refs hide
    /// `cally` at the soft hyphen and paint the hyphen (ref-a: U+2010). The
    /// word-only cut showed `…room…`, 17 cells short of the ref.
    func testClampHidesAtTheSoftHyphenOfBlockEllipsis028() {
        let text = "This time, Mark, who had always been the center of attention in any social gathering, "
            + "walked into the room uncharacteristi\u{AD}cally quietly, barely speaking as he settled into a chair. "
            + "When asked, he said that he was fine, when he wasn't really fine."
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: text, maxWidth: 63.1 * ch, measure: mono,
                                    clamp: GreedyLineBreaker.Clamp(lines: 2)),
            ["This time, Mark, who had always been the center of attention in",
             "any social gathering, walked into the room uncharacteristi\u{2010}\u{2026}"])
    }

    /// UAX #14 ID/SA opportunities sit BETWEEN letters and are not modelled:
    /// a hidden tail holding one DECLINES (lines untouched — wave 51's
    /// `.lineLimit` truncation stays); an ideograph-free tail still trims.
    func testClampDeclinesWhenTheHiddenTailHoldsAnIdeograph() {
        let cjk = ["aa bb", "字字字字字字", "cc"]
        XCTAssertEqual(GreedyLineBreaker.clampLines(cjk, clamp: .init(lines: 2), maxWidth: 5 * ch, measure: mono), cjk)
        // Control: the ideographs stay in the KEPT part, the tail is Latin.
        XCTAssertEqual(GreedyLineBreaker.clampLines(["x", "字字 aa bb", "y"], clamp: .init(lines: 2),
                                                    maxWidth: 6 * ch, measure: mono), ["x", "字字 aa\u{2026}"])
    }

    /// Break-after hyphens/dashes and ZWSP are opportunities; `1-2`
    /// (HY × NU) and a word-initial hyphen (LB20.1) are not.
    func testClampHidesAfterAHyphenAndAtAZeroWidthSpace() {
        // Line N of a 3-line paragraph, clamp 2, a 6ch box.
        func cut(_ s: String) -> [String] {
            GreedyLineBreaker.clampLines(["x", s, "y"], clamp: .init(lines: 2), maxWidth: 6 * ch, measure: mono)
        }
        XCTAssertEqual(cut("foo-barbaz"), ["x", "foo-\u{2026}"])
        XCTAssertEqual(cut("foo\u{200B}barbaz"), ["x", "foo\u{2026}"])
        XCTAssertEqual(cut("foo-1234"), ["x", "\u{2026}"])
        XCTAssertEqual(cut("-foobarbaz"), ["x", "\u{2026}"])
    }

    // MARK: - drawnClamp: which labels bake a marker at all

    /// The seam's ONE reader (ComponentRenderer.swift passes
    /// `style.text.lineClampLimit` + the component's own declarations).
    /// Wires VERBATIM off wave51-fix/sections/css-overflow/per-test-ir.
    /// MUTATION PROOF (executed 2026-10-05, mutate.py S7 —
    /// mutations-swift.result.json): with the LineClampExtractor guard
    /// dropped (any limit bakes a marker) this test fails (a Clamp returned
    /// for the bare `max-lines` and for the suppressed marker). Restored
    /// byte-exact, sha256-verified.
    func testDrawnClampOnlyForAFixedCountLineClampWithAMarker() {
        // block-ellipsis-025: `line-clamp: 4` → the label's limit 4 bakes.
        let clamp025 = [IRProperty(type: "LineClamp",
                                   data: .object(["type": .string("lines"), "count": .int(4)]))]
        XCTAssertEqual(GreedyLineBreaker.drawnClamp(limit: 4, properties: clamp025)?.lines, 4)
        XCTAssertEqual(GreedyLineBreaker.drawnClamp(limit: 4, properties: clamp025)?.marker, "\u{2026}")
        // discard-multicol-004: `max-lines: 5` ALONE — block-ellipsis stays
        // its initial `none` (css-overflow-4 §5.1), so no marker is baked
        // even though TypographyAggregate folds it into the same limit.
        let maxLines004 = [IRProperty(type: "MaxLines",
                                      data: .object(["type": .string("count"), "value": .int(5)]))]
        XCTAssertNil(GreedyLineBreaker.drawnClamp(limit: 5, properties: maxLines004))
        // block-ellipsis-023: `line-clamp: 4 no-ellipsis` — suppressed.
        let clamp023 = [IRProperty(type: "LineClamp",
                                   data: .object(["type": .string("lines"), "count": .int(4),
                                                  "ellipsis": .object(["type": .string("no-ellipsis")])]))]
        XCTAssertNil(GreedyLineBreaker.drawnClamp(limit: 4, properties: clamp023))
        // No limit reached the label (LineClampCap nils a suppressed one).
        XCTAssertNil(GreedyLineBreaker.drawnClamp(limit: nil, properties: clamp025))
    }

    // MARK: - Wave 53 (lane L2, F1-iOS): what an admitted space-less run folds to

    /// hyphens-span-001's `high<U+00AD>way` (VERBATIM, wave52-ship per-test IR
    /// `…hyphens-span-001__1-301`) in the ref's 6ch box: `highway` (7ch) does
    /// not fit, so the opening word breaks at its soft hyphen and the UA
    /// hyphen is painted (css-text-3 §5.3) — the two lines the seam-1 label
    /// now pins at 2 × the line box. Same lines as the Kotlin twin's
    /// PreBreakPipelineTest pin (a).
    func testASpacelessSoftHyphenWordBreaksAtItsSoftHyphen() {
        XCTAssertEqual(GreedyLineBreaker.lines(text: "high\u{AD}way", maxWidth: 6 * ch, measure: mono),
                       ["high\u{2010}", "way"])
    }

    /// hyphenate-character-001's run piece 1 (VERBATIM, `…__1-177`
    /// meta.runs[0].text) at that test's 4.5ch: every usable soft hyphen is
    /// taken — the ref's five lines (U+2010 baked; BACKLOG 4(f)). Kotlin
    /// twin: PreBreakPipelineTest pin (d).
    func testAMultiOpportunitySpacelessRunSplitsIntoFiveLines() {
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "im\u{AD}ple\u{AD}men\u{AD}ta\u{AD}tion", maxWidth: 4.5 * ch, measure: mono),
            ["im\u{2010}", "ple\u{2010}", "men\u{2010}", "ta\u{2010}", "tion"])
    }

    // MARK: - Wave 54 (lane L3, U2-ios) — the hyphenate-character string
    //
    // css-text-4 §6.3: the string a taken soft hyphen paints enters the fold
    // (its width decides the breaks) and, once painted at a line end, is
    // SPENT (css-text-3 §5.5) — SpentHyphen.swift. Inputs are
    // hyphenate-character-001/-004's host meta.runs pieces VERBATIM
    // (wave53-final css-text per-test-ir); boxes are those tests' 4.5ch /
    // 6.5ch. Kotlin twin: PreBreakPipelineTest pins (a)-(d).
    //
    // MUTATION PROOF (executed; tools/titan/results/wave54-hyphenate-character/
    // mutations.log): U2I-a drop the strip in SpentHyphen.opportunityText →
    // the spent-hyphen pin fails; U2I-b the extractor lower-cases / reads
    // `value` before `type` → the extractor pins fail. Restored byte-exact.

    /// hyphenate-character-004's piece 2 at 6.5ch with `/-/`: `tial/-/` is
    /// 7ch and overflows (the ref's overflowing line), four lines in all.
    func testASlashHyphenSlashStringIsMeasuredInTheFold() {
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: " ini\u{AD}tial\u{AD}iza\u{AD}tion", maxWidth: 6.5 * ch,
                                    measure: mono, hyphenChar: "/-/"),
            ["ini/-/", "tial/-/", "iza/-/", "tion"])
    }

    /// hyphenate-character-001 with `""`: nothing painted, and `real` (4ch)
    /// fits 4.5ch — the ref's `real/iza/tion`, not `re‐/al‐/iza‐/tion`.
    func testAnEmptyStringPaintsNothingAndWidensTheFit() {
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "im\u{AD}ple\u{AD}men\u{AD}ta\u{AD}tion", maxWidth: 4.5 * ch,
                                    measure: mono, hyphenChar: ""),
            ["im", "ple", "men", "ta", "tion"])
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: " re\u{AD}al\u{AD}iza\u{AD}tion", maxWidth: 4.5 * ch,
                                    measure: mono, hyphenChar: ""),
            ["real", "iza", "tion"])
    }

    /// The lone-hyphen defect: `tial/-/` overflows ONLY by the hyphen its own
    /// taken break painted. With the string spent the line has nowhere left
    /// to break, so rule B claims it (TextKit no longer re-wraps it); nil —
    /// every run that declares nothing — keeps the wave-53 answer.
    func testALineOverflowingOnlyByItsSpentHyphenIsUnbreakable() {
        XCTAssertTrue(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            ["tial/-/"], maxWidth: 6.5 * ch, spentHyphen: "/-/", measure: mono))
        XCTAssertFalse(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            ["tial/-/"], maxWidth: 6.5 * ch, measure: mono))
        // The UA population is untouched: `tial‐` (001 today) stays breakable.
        XCTAssertFalse(GreedyLineBreaker.hasUnbreakableOverflowingLine(
            ["tial\u{2010}"], maxWidth: 4.5 * ch, measure: mono))
    }

    /// SpentHyphen is identity unless the line ENDS with the string and has
    /// ink before it; it strips exactly one occurrence.
    func testSpentHyphenStripsOneTrailingOccurrenceOnly() {
        XCTAssertEqual(SpentHyphen.opportunityText("tial/-/", spentHyphen: "/-/"), "tial")
        XCTAssertEqual(SpentHyphen.opportunityText("a/-//-/", spentHyphen: "/-/"), "a/-/")
        XCTAssertEqual(SpentHyphen.opportunityText("tial/-/", spentHyphen: nil), "tial/-/")
        XCTAssertEqual(SpentHyphen.opportunityText("tial", spentHyphen: ""), "tial")
        XCTAssertEqual(SpentHyphen.opportunityText("/-/", spentHyphen: "/-/"), "/-/")
        XCTAssertEqual(SpentHyphen.opportunityText("ta-b", spentHyphen: "/-/"), "ta-b")
    }

    /// The extractor on the wire's object shape: -004's `/-/` verbatim, `""`
    /// as a value, a cased string never folded, a quoted "auto" a string.
    func testTheExtractorReadsTheObjectShapeVerbatim() {
        func cfg(_ o: [String: IRValue]) -> HyphenateCharacterConfig? {
            HyphenateCharacterExtractor.extract(from: [IRProperty(type: "HyphenateCharacter", data: .object(o))])
        }
        XCTAssertEqual(cfg(["type": .string("string"), "value": .string("/-/")])?.value, "/-/")
        XCTAssertEqual(cfg(["type": .string("string"), "value": .string("")]), HyphenateCharacterConfig(value: ""))
        XCTAssertEqual(cfg(["type": .string("string"), "value": .string("AbC")])?.value, "AbC")
        XCTAssertEqual(cfg(["type": .string("string"), "value": .string("auto")])?.value, "auto")
        XCTAssertEqual(cfg(["type": .string("auto")]), HyphenateCharacterConfig(value: nil))
        XCTAssertNil(HyphenateCharacterExtractor.extract(from: []))
    }

    /// Applier → aggregate: a string touches the aggregate (so StyleBuilder
    /// mirrors it into TextConfig); `auto` leaves a fresh aggregate untouched.
    /// End to end, StyleBuilder.build puts -004's verbatim wire string on
    /// TextConfig — the field seam-2 reads (mutation U2I-d: drop the mirror).
    func testTheApplierCarriesTheStringOntoTheAggregate() {
        var agg = TypographyAggregate()
        HyphenateCharacterApplier.contribute(HyphenateCharacterConfig(value: ""), into: &agg)
        XCTAssertEqual(agg.hyphenateCharacter, "")
        XCTAssertTrue(agg.touched)
        var autoAgg = TypographyAggregate()
        HyphenateCharacterApplier.contribute(HyphenateCharacterConfig(value: nil), into: &autoAgg)
        XCTAssertNil(autoAgg.hyphenateCharacter)
        XCTAssertFalse(autoAgg.touched)
        let wire004 = IRProperty(type: "HyphenateCharacter", data: .object(["type": .string("string"), "value": .string("/-/")]))
        XCTAssertEqual(StyleBuilder.build(from: [wire004]).text.hyphenateCharacter, "/-/")
        XCTAssertNil(StyleBuilder.build(from: []).text.hyphenateCharacter)
    }
}
