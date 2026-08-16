import XCTest
@testable import StyleConverterRuntime

/// Wave 44, lane U2 — the inline-run FLOW fold
/// (StyleEngine/typography/inline/InlineRunFlow.swift).
///
/// WHY THIS EXISTS. Wave 32's InlineRunPlan bought `meta.runs` the correct
/// ORDER, but each entry still rendered as a STACKED label in the block
/// VStack — `<div>DNA <span>…</span>.</div>` painted four lines where
/// Chromium flows one paragraph (css-text/hyphens-manual-inline-010:
/// ios-ref 0.6499 at wave43-final). The fold under test decides when the
/// runs ARE one paragraph (metrics-uniform plain spans) and folds them
/// into the single string the leaf-text machinery renders; everything
/// else refuses back to the stacked path.
///
/// The payloads below are VERBATIM from the wave43-final consumed IR
/// (tools/titan/runs/wave43-final/sections/*/per-test-ir/) — the exact
/// wire the fold must serve, not idealized shapes.
final class InlineRunFlowTests: XCTestCase {

    // MARK: - Construction helpers (the InlineRunPlanTests idiom)

    /// A referenced member child: name doubles as the authoring key the
    /// wire's `child` entries carry on the converter-emitted path.
    private func member(_ name: String,
                        tag: String? = "span",
                        text: String?,
                        props: [IRProperty] = [],
                        lang: String? = nil,
                        decorations: [IRDecoration]? = nil,
                        markerText: String? = nil,
                        runs: [IRRun]? = nil,
                        children: [IRComponent]? = nil) -> IRComponent {
        IRComponent(id: name + "-id", name: name, properties: props,
                    selectors: nil, media: nil, children: children, slot: nil,
                    text: text, pseudos: nil,
                    meta: IRMeta(sourceTag: tag, decorations: decorations,
                                 markerText: markerText, lang: lang, runs: runs))
    }

    // MARK: - The engaged victims (verbatim payloads)

    func testHyphensManualInline010FoldsToOneParagraph() {
        // wpt__css-text__hyphens__hyphens-manual-inline-010: the div's wire
        // is runs [text "DNA ", child span, text "."], span text
        // "means Deoxyribonucleic acid" with Hyphens MANUAL + lang en.
        // The container declares no Hyphens — initial `manual`, so the
        // member's declaration is EQUIVALENT and must not refuse.
        let span = member("hyphens__hyphens-manual-inline-010__1__0",
                          text: "means Deoxyribonucleic acid",
                          props: [IRProperty(type: "Hyphens", data: .string("MANUAL"))],
                          lang: "en")
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "DNA "),
                   IRRun(child: "hyphens__hyphens-manual-inline-010__1__0"),
                   IRRun(text: ".")],
            children: [span], totalChildCount: 1, containerProperties: [])
        // ONE paragraph — the ref's "DNA means Deoxyribon|ucleic / acid."
        // line breaking is then the greedy pre-break's job downstream.
        XCTAssertEqual(folded, "DNA means Deoxyribonucleic acid.")
    }

    func testHyphensManualInline011SoftHyphensSurviveTheFold() {
        // …-011: the span text carries TWO U+00AD soft hyphens
        // ("Deoxy\u{AD}ribo\u{AD}nucleic"). The fold must hand them
        // through VERBATIM — SoftHyphenPolicy + WordBreakOpportunities own
        // them inside PlaceholderLabel (css-text-3 §6.1 `manual`), and a
        // fold that stripped them would erase the test's whole subject.
        let span = member("hyphens__hyphens-manual-inline-011__0__0",
                          text: "means Deoxy\u{AD}ribo\u{AD}nucleic acid",
                          props: [IRProperty(type: "Hyphens", data: .string("MANUAL"))])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "DNA "),
                   IRRun(child: "hyphens__hyphens-manual-inline-011__0__0"),
                   IRRun(text: ".")],
            children: [span], totalChildCount: 1, containerProperties: [])
        XCTAssertEqual(folded, "DNA means Deoxy\u{AD}ribo\u{AD}nucleic acid.")
    }

    func testHyphensManualInline012FourSoftHyphensSurvive() {
        // …-012: four soft hyphens ("Deo\u{AD}xy\u{AD}ribo\u{AD}nu\u{AD}cleic")
        // — same contract at higher density, the 8ch box's ref breaks at
        // the LAST fitting opportunity per line.
        let span = member("hyphens__hyphens-manual-inline-012__0__0",
                          text: "means Deo\u{AD}xy\u{AD}ribo\u{AD}nu\u{AD}cleic acid",
                          props: [IRProperty(type: "Hyphens", data: .string("MANUAL"))])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "DNA "),
                   IRRun(child: "hyphens__hyphens-manual-inline-012__0__0"),
                   IRRun(text: ".")],
            children: [span], totalChildCount: 1, containerProperties: [])
        XCTAssertEqual(folded,
                       "DNA means Deo\u{AD}xy\u{AD}ribo\u{AD}nu\u{AD}cleic acid.")
    }

    // MARK: - The refusing victims (verbatim payloads)

    func testInheritComputed001EmptyBorderedEmRefuses() {
        // wpt__CSS2__cascade__inherit-computed-001: the p's runs interleave
        // a SUPPORTED plain span ("in one font", no properties) with an
        // EMPTY `<em>` that paints `border: inherit` (medium solid) — an
        // inline ATOM whose visible contribution is its own box, which a
        // text fold cannot express. The WHOLE fold must refuse (partial
        // engagement would drop the em's ink from the composition).
        let span = member("cascade__inherit-computed-001__0__0",
                          text: "in one font")
        let em = member("cascade__inherit-computed-001__0__1", tag: "em",
                        text: nil,
                        props: [IRProperty(type: "Color",
                                           data: .object(["original": .string("red")])),
                                IRProperty(type: "BorderTopColor",
                                           data: .object(["original": .string("inherit")]))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "This line is all "),
                   IRRun(child: "cascade__inherit-computed-001__0__0"),
                   IRRun(text: " size "),
                   IRRun(child: "cascade__inherit-computed-001__0__1"),
                   IRRun(text: " and there is no red.")],
            children: [span, em], totalChildCount: 2, containerProperties: []))
    }

    func testCounterListItemBlockOlMembersRefuse() {
        // wpt__css-lists__counter-list-item: the floated div's runs
        // reference three `<ol>` children — BLOCK boxes that interrupt the
        // inline flow with anonymous blocks (CSS 2.1 §9.2.1.1). The
        // stacked path IS the correct shape for them; the fold refuses.
        let ols = (0..<3).map {
            member("counter-list-item__1__\($0)", tag: "ol", text: nil)
        }
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "Ordered lists "),
                   IRRun(child: "counter-list-item__1__0"), IRRun(text: " "),
                   IRRun(child: "counter-list-item__1__1"), IRRun(text: " "),
                   IRRun(child: "counter-list-item__1__2")],
            children: ols, totalChildCount: 3, containerProperties: []))
    }

    func testHyphensAutoInline010ModeMismatchRefuses() {
        // wpt__css-text__hyphens__hyphens-auto-inline-010: the span asks
        // for DICTIONARY hyphenation (`hyphens: auto`, lang en) while the
        // container sits on the initial `manual` — folding with ONE mode
        // would either hyphenate the container's words (spec violation) or
        // strip the span's dictionary (regression vs the ref). Refuse;
        // per-segment break modes are the named deferred ring.
        let span = member("hyphens__hyphens-auto-inline-010__0__0",
                          text: "new engines now",
                          props: [IRProperty(type: "Hyphens", data: .string("AUTO"))],
                          lang: "en")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "There are "),
                   IRRun(child: "hyphens__hyphens-auto-inline-010__0__0"),
                   IRRun(text: ".")],
            children: [span], totalChildCount: 1, containerProperties: []))
    }

    func testDetailsSummaryMemberRefuses() {
        // wpt__css-ui__appearance-auto-details-list-item (PASSING 0.968 at
        // wave43-final): `<summary>` is `display: list-item` in the UA
        // sheet — a BLOCK member the stacked path already renders right.
        // The fold must not engage and disturb a passing capture.
        let summary = member("appearance-auto-details-list-item__0__0",
                             tag: "summary", text: "Summary")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(child: "appearance-auto-details-list-item__0__0"),
                   IRRun(text: " Details")],
            children: [summary], totalChildCount: 1, containerProperties: []))
    }

    // MARK: - Structural gates

    func testUnclaimedChildRefuses() {
        // A plan naming one of TWO children leaves a box that would render
        // AGAIN outside the paragraph — refuse (all-claimed gate).
        let a = member("a", text: "alpha")
        let b = member("b", text: "beta")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [a, b], totalChildCount: 2, containerProperties: []))
    }

    func testOutOfFlowSiblingRefuses() {
        // totalChildCount counts composed children INCLUDING out-of-flow
        // boxes the in-flow array omits: an abspos sibling's static
        // position depends on the very flow this fold replaces
        // (CSS 2.1 §10.3.7) — refuse until the abspos ring lands.
        let a = member("a", text: "alpha")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [a], totalChildCount: 2, containerProperties: []))
    }

    func testOutOfOrderAndDuplicateRefsRefuse() {
        // The InlineRunPlan proof, same refusal here: refs must land in
        // strictly increasing rendered order (FlexboxApplier.sorted may
        // permute by CSS `order`), and a duplicate would double glyphs.
        let a = member("a", text: "alpha")
        let b = member("b", text: "beta")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(child: "b"), IRRun(child: "a")],
            children: [a, b], totalChildCount: 2, containerProperties: []))
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(child: "a"), IRRun(child: "a")],
            children: [a], totalChildCount: 1, containerProperties: []))
    }

    func testDanglingRefRefuses() {
        // A dangling key names glyph content this composition cannot see —
        // a paragraph with a hole must not replace the stacked path.
        let a = member("a", text: "alpha")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(child: "nobody"), IRRun(child: "a")],
            children: [a], totalChildCount: 1, containerProperties: []))
    }

    func testVerticalWritingModeRefuses() {
        // css-writing-modes: the fold reuses the HORIZONTAL leaf machinery;
        // a vertical container (direction-upright-002's shape) must keep
        // its existing path. Wire spelling per the corpus: "VERTICAL_RL".
        let a = member("a", text: "alpha")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [a], totalChildCount: 1,
            containerProperties: [IRProperty(type: "WritingMode",
                                             data: .string("VERTICAL_RL"))]))
    }

    func testMemberStyleAndDecorationWiresRefuse() {
        // A member's own Color is the styled-span ring (folding would ink
        // it with the parent's color); a member's own decoration wire
        // attaches to a box the fold erases. Both refuse.
        let colored = member("a", text: "alpha",
                             props: [IRProperty(type: "Color",
                                                data: .string("red"))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [colored], totalChildCount: 1, containerProperties: []))
        let decorated = member("a", text: "alpha",
                               decorations: [IRDecoration(line: "underline")])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [decorated], totalChildCount: 1, containerProperties: []))
    }

    func testNestedMemberRefuses() {
        // A member with its own children (or runs) is a nested inline
        // TREE — flattening here would guess an order the wire spells out
        // one level down. Deferred until the fold recurses.
        let inner = member("inner", text: "deep")
        let nested = member("a", text: "alpha", children: [inner])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [nested], totalChildCount: 1, containerProperties: []))
    }

    func testContainerHyphensNoneVsMemberManualRefuses() {
        // The equivalence anchor is the container's EFFECTIVE mode: a
        // `none` container deletes soft hyphens (SoftHyphenPolicy) while a
        // `manual` member honours them — different display strings, so the
        // member's declaration is NOT inert. Refuse.
        let span = member("a", text: "al\u{AD}pha",
                          props: [IRProperty(type: "Hyphens", data: .string("MANUAL"))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [span], totalChildCount: 1,
            containerProperties: [IRProperty(type: "Hyphens",
                                             data: .string("NONE"))]))
    }

    func testWhitespaceOnlyRunSurvivesEmptyOneDropped() {
        // InlineRunPlan rule 6, inherited verbatim: the single space
        // between two spans IS the inter-run word space (real advance
        // width — css-text-3 §4.1's collapsed segment); a zero-length run
        // contributes neither glyphs nor a box.
        let a = member("a", text: "alpha")
        let b = member("b", text: "beta")
        let folded = InlineRunFlow.fold(
            runs: [IRRun(child: "a"), IRRun(text: " "),
                   IRRun(text: ""), IRRun(child: "b")],
            children: [a, b], totalChildCount: 2, containerProperties: [])
        XCTAssertEqual(folded, "alpha beta")
    }

    func testBrMemberRefusesOnItsOwnNamedWall() {
        // `<br>` is a FORCED break, not an atom — a real "\n" candidate
        // for a future ring, refused today (123 references at wave43-final
        // are unstudied pixel-wise). This pin documents the wall so
        // engaging it later is a deliberate flip, not drift.
        let br = member("a", tag: "br", text: nil)
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x"), IRRun(child: "a"), IRRun(text: "y")],
            children: [br], totalChildCount: 1, containerProperties: []))
    }

    func testAbsentAndTextOnlyInputsStaySilentNil() {
        // The overwhelming common case (every non-runs component) and the
        // nothing-to-place case: nil, mirroring InlineRunPlan's contract.
        let a = member("a", text: "alpha")
        XCTAssertNil(InlineRunFlow.fold(runs: nil, children: [a],
                                        totalChildCount: 1, containerProperties: []))
        XCTAssertNil(InlineRunFlow.fold(runs: [], children: [a],
                                        totalChildCount: 1, containerProperties: []))
        XCTAssertNil(InlineRunFlow.fold(runs: [IRRun(text: "x")], children: [],
                                        totalChildCount: 0, containerProperties: []))
    }
}
