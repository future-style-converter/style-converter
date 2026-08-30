import XCTest
@testable import StyleConverterRuntime

/// Wave 44, lane U2 — the inline-run FLOW fold
/// (StyleEngine/typography/inline/InlineRunFlow.swift) — updated by
/// wave 45 lane X1 for the Compose-parity gate (member rings, empty-member
/// drop, hyphens adoption, host text-transform/tab rails, cross-boundary
/// whitespace collapse).
///
/// WHY THIS EXISTS. Wave 32's InlineRunPlan bought `meta.runs` the correct
/// ORDER, but each entry still rendered as a STACKED label in the block
/// VStack — `<div>DNA <span>…</span>.</div>` painted four lines where
/// Chromium flows one paragraph (css-text/hyphens-manual-inline-010:
/// ios-ref 0.6499 at wave43-final). The fold under test decides when the
/// runs ARE one paragraph and folds them into the single string the
/// leaf-text machinery renders; everything else refuses back to the
/// stacked path. The wave-44 gate then measured the iOS gate STRICTER
/// than the Compose twin's on two victims — hyphens-auto-inline-010
/// (bailed here, folded + passed on Android) and CSS2 inherit-computed-001
/// (the bordered empty `<em>`) — which lane X1 closed; the pins below
/// cover both the widened admissions and every refusal that must survive.
///
/// The payloads below are VERBATIM from the wave43/44 consumed IR
/// (tools/titan/runs/wave4x-final/sections/*/per-test-ir/) — the exact
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
        // The container declares no Hyphens — the member's MANUAL is
        // ADOPTED (X1), which is behavior-neutral downstream: `manual` is
        // the css-text-3 §6.1 initial the label already assumes.
        let span = member("hyphens__hyphens-manual-inline-010__1__0",
                          text: "means Deoxyribonucleic acid",
                          props: [IRProperty(type: "Hyphens", data: .string("MANUAL"))],
                          lang: "en")
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "DNA "),
                   IRRun(child: "hyphens__hyphens-manual-inline-010__1__0"),
                   IRRun(text: ".")],
            children: [span], totalChildCount: 1, containerProperties: [],
            containerLang: "en")
        // ONE paragraph — the ref's "DNA means Deoxyribon|ucleic / acid."
        // line breaking is then the greedy pre-break's job downstream.
        XCTAssertEqual(folded?.text, "DNA means Deoxyribonucleic acid.")
        // Member declaration adopted, in the extractor's lowercase spelling.
        XCTAssertEqual(folded?.adoptedHyphensMode, "manual")
        XCTAssertEqual(folded?.droppedEmptyMembers, 0)
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
            children: [span], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "DNA means Deoxy\u{AD}ribo\u{AD}nucleic acid.")
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
            children: [span], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(folded?.text,
                       "DNA means Deo\u{AD}xy\u{AD}ribo\u{AD}nu\u{AD}cleic acid.")
    }

    func testHyphensAutoInline010AutoAdoptedWithMatchingLanguage() {
        // wpt__css-text__hyphens__hyphens-auto-inline-010 — the wave-44
        // measured asymmetry (android-ref 0.9695 folded vs ios-ref 0.9264
        // bailed): the span asks for DICTIONARY hyphenation (`hyphens:
        // auto`, lang en) while the container declares none. X1 parity:
        // the member's mode is ADOPTED for the paragraph — exact here
        // because the host text ("There are ", ".") is break-free — and
        // the caller hands it to AutoHyphenation with the container's
        // matching lang ("en" == "en", the adoption gate's own proof).
        let span = member("hyphens__hyphens-auto-inline-010__0__0",
                          text: "new engines now",
                          props: [IRProperty(type: "Hyphens", data: .string("AUTO"))],
                          lang: "en")
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "There are "),
                   IRRun(child: "hyphens__hyphens-auto-inline-010__0__0"),
                   IRRun(text: ".")],
            children: [span], totalChildCount: 1, containerProperties: [],
            containerLang: "en")
        // One paragraph; the ref's "en-/gines" dictionary break is then
        // the pre-break + AutoHyphenation's job downstream.
        XCTAssertEqual(folded?.text, "There are new engines now.")
        XCTAssertEqual(folded?.adoptedHyphensMode, "auto")
    }

    func testInheritComputed001EmptyBorderedEmDropsAndSpacesCollapse() {
        // wpt__CSS2__cascade__inherit-computed-001 — the other wave-44
        // asymmetry (android-ref 0.8933 folded vs ios-ref 0.8369 stacked):
        // the p's runs interleave a plain span ("in one font") with an
        // EMPTY `<em>` carrying Color red + `border: inherit` COLORS whose
        // style never reached the wire (converter gap) — paint-inert
        // without glyphs or border styles, so the member is admitted and
        // DROPPED (X1, the Compose empty-member ring). Its flanking
        // spaces (" size " + " and …") collapse to ONE (css-text-3
        // §4.1.1), exactly as the Chromium ref paints "size ▮and". The
        // ref's ▮ bar (the em's collapsed border box) is the fold's
        // stated loss — the atom-box ring stays deferred.
        let span = member("cascade__inherit-computed-001__0__0",
                          text: "in one font")
        let em = member("cascade__inherit-computed-001__0__1", tag: "em",
                        text: nil,
                        props: [IRProperty(type: "Color",
                                           data: .object(["srgb": .object(["r": .int(1), "g": .int(0), "b": .int(0)]),
                                                          "original": .string("red")])),
                                IRProperty(type: "BorderTopColor",
                                           data: .object(["original": .string("inherit")])),
                                IRProperty(type: "BorderRightColor",
                                           data: .object(["original": .string("inherit")])),
                                IRProperty(type: "BorderBottomColor",
                                           data: .object(["original": .string("inherit")])),
                                IRProperty(type: "BorderLeftColor",
                                           data: .object(["original": .string("inherit")]))])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "This line is all "),
                   IRRun(child: "cascade__inherit-computed-001__0__0"),
                   IRRun(text: " size "),
                   IRRun(child: "cascade__inherit-computed-001__0__1"),
                   IRRun(text: " and there is no red.")],
            children: [span, em], totalChildCount: 2, containerProperties: [],
            containerLang: nil)
        // ONE space between "size" and "and" — the cross-boundary collapse.
        XCTAssertEqual(folded?.text,
                       "This line is all in one font size and there is no red.")
        // The drop is REPORTED (the caller leaves the breadcrumb).
        XCTAssertEqual(folded?.droppedEmptyMembers, 1)
        // No member declared Hyphens → nothing adopted.
        XCTAssertNil(folded?.adoptedHyphensMode)
    }

    // MARK: - The widened rings (X1 admissions beyond the victims)

    func testTimeAndDataTagsFoldLikeSpan() {
        // `time`/`data` add NO UA styling (HTML rendering §15.3) — the
        // only two tags beside `span` in the TEXT ring, Compose parity.
        let t = member("t", tag: "time", text: "yesterday")
        let d = member("d", tag: "data", text: "42")
        let folded = InlineRunFlow.fold(
            runs: [IRRun(child: "t"), IRRun(text: " and "), IRRun(child: "d")],
            children: [t, d], totalChildCount: 2, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "yesterday and 42")
    }

    func testBorderColorOnlyTextMemberIsPaintInert() {
        // border-*-color with NO border-*-style paints nothing
        // (css-backgrounds-3 §3.2 initial `border-style: none`) — a span
        // carrying only colors folds with nothing to report.
        let inert = member("a", text: "alpha",
                           props: [IRProperty(type: "BorderTopColor",
                                              data: .object(["original": .string("red")]))])
        let inertFold = InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [inert], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(inertFold?.text, "x alpha")
        XCTAssertEqual(inertFold?.spans.isEmpty, true)
        // Wave 47 (lane Z6): a STYLE makes the border a real box — which
        // the styled-span ring now folds as a STATED LOSS (the fold's
        // paragraph geometry beats the stacked full-width block —
        // block-ellipsis-004's measured margin), reported per member so
        // the seam logs the dropped ink instead of refusing the fold.
        let styled = member("a", text: "alpha",
                            props: [IRProperty(type: "BorderTopColor",
                                               data: .object(["original": .string("red")])),
                                    IRProperty(type: "BorderTopStyle",
                                               data: .string("SOLID"))])
        let styledFold = InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [styled], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(styledFold?.text, "x alpha")
        XCTAssertEqual(styledFold?.spans.count, 1)
        XCTAssertEqual(styledFold?.spans.first?.statedLossTypes.first?
            .hasPrefix("border-box-ink("), true)
    }

    func testCrossBoundarySpaceCollapse() {
        // "a " + " b" → "a b": one collapsible space survives the member
        // boundary (css-text-3 §4.1.1 phase-1) — the Compose twin's pin.
        let b = member("b", text: " b")
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "a "), IRRun(child: "b")],
            children: [b], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "a b")
    }

    func testUAStyledTagWithGlyphsStillRefuses() {
        // Wave 48 (lane W4) models the UA italic family and sup/sub, but
        // `strong`'s UA `font-weight: bolder` (HTML rendering §15.3.4) is
        // RELATIVE to the inherited weight — machinery the ring does not
        // carry (zero corpus presence) — so a glyph-bearing strong still
        // refuses rather than fold without its bolding.
        let strong = member("a", tag: "strong", text: "important")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [strong], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    func testItalicFamilyMemberFoldsWithTheUASlant() {
        // Wave 48 (lane W4): an `<em>` WITH glyphs folds, its UA italic
        // riding as a span (HTML rendering §15.3.4 — the exact rule the
        // wave-45 refusal above used to protect; the ring now models it).
        let em = member("a", tag: "em", text: "emphasised")
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [em], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "x emphasised")
        XCTAssertEqual(folded?.spans.count, 1)
        XCTAssertEqual(folded?.spans.first?.style.italic, true)
        XCTAssertNil(folded?.spans.first?.style.shift)
    }

    func testStrutBearingEmptyMemberRefuses() {
        // FontSize on an EMPTY inline still contributes its strut to the
        // line box (CSS 2.1 §10.8) — dropping it would shrink the line,
        // so the inert set excludes it (Compose parity pin).
        let strut = member("a", tag: "em", text: nil,
                           props: [IRProperty(type: "FontSize",
                                              data: .object(["px": .int(32)]))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [strut], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    // MARK: - Hyphens adoption rules (X1, css-text-3 §6.1)

    func testMemberContradictingHostDeclarationRefuses() {
        // The host DECLARED `none` (deletes soft hyphens downstream); a
        // member's `manual` honours them — different display strings, so
        // no single paragraph mode is faithful. Refuse.
        let span = member("a", text: "al\u{AD}pha",
                          props: [IRProperty(type: "Hyphens", data: .string("MANUAL"))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [span], totalChildCount: 1,
            containerProperties: [IRProperty(type: "Hyphens",
                                             data: .string("NONE"))],
            containerLang: nil))
    }

    func testMemberAgreeingWithHostDeclarationIsInert() {
        // Host declared `manual`, member declares `manual` — agreement is
        // inert (no adoption: the host's own declaration already governs).
        let span = member("a", text: "alpha",
                          props: [IRProperty(type: "Hyphens", data: .string("MANUAL"))])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [span], totalChildCount: 1,
            containerProperties: [IRProperty(type: "Hyphens",
                                             data: .string("MANUAL"))],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "x alpha")
        XCTAssertNil(folded?.adoptedHyphensMode)
    }

    func testTwoMembersDisagreeingRefuse() {
        // MANUAL then AUTO under an undeclared host: the first was
        // adopted, the second contradicts it — no single mode. Refuse.
        let a = member("a", text: "alpha",
                       props: [IRProperty(type: "Hyphens", data: .string("MANUAL"))])
        let b = member("b", text: "beta",
                       props: [IRProperty(type: "Hyphens", data: .string("AUTO"))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(child: "a"), IRRun(text: " "), IRRun(child: "b")],
            children: [a, b], totalChildCount: 2, containerProperties: [],
            containerLang: nil))
    }

    func testAutoAdoptionWithDivergingLanguageRefuses() {
        // Paragraph language en, member `auto` tagged fr — a French
        // dictionary cannot be selected for the whole paragraph (§6.1
        // "appropriate to the language of the text"). Refuse.
        let span = member("a", text: "alpha",
                          props: [IRProperty(type: "Hyphens", data: .string("AUTO"))],
                          lang: "fr")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [span], totalChildCount: 1, containerProperties: [],
            containerLang: "en"))
    }

    func testAutoAdoptionWithNilMemberLangInheritsTheHosts() {
        // A nil member lang INHERITS the host's (the producer's own
        // ladder) — trivially equal, so adoption engages.
        let span = member("a", text: "alpha",
                          props: [IRProperty(type: "Hyphens", data: .string("AUTO"))])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [span], totalChildCount: 1, containerProperties: [],
            containerLang: "en")
        XCTAssertEqual(folded?.adoptedHyphensMode, "auto")
    }

    // MARK: - Host gates (X1 parity rails)

    func testHostTextTransformRefuses() {
        // text-transform rewrites the merged string downstream (the label
        // applies it to the WHOLE paragraph); a length-changing transform
        // would move member boundaries — refuse (zero corpus hosts carry
        // one, so this is a pure safety rail, Compose parity).
        let span = member("a", text: "alpha")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [span], totalChildCount: 1,
            containerProperties: [IRProperty(type: "TextTransform",
                                             data: .string("UPPERCASE"))],
            containerLang: nil))
    }

    func testTabInMergedTextRefuses() {
        // A tab would be rewritten by tab-size expansion downstream,
        // shifting member boundaries — refuse (Compose parity rail).
        let span = member("a", text: "beta")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x\ty "), IRRun(child: "a")],
            children: [span], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    // MARK: - The refusing shapes (verbatim payloads, pre-X1 pins kept)

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
            children: ols, totalChildCount: 3, containerProperties: [],
            containerLang: nil))
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
            children: [summary], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    func testAbsposMemberRefusesOnItsPositionProperty() {
        // wpt__css-text__hyphens__hyphens-out-of-flow-001 host __3: the
        // abspos span is OUT of the member property rings (it needs a
        // static-position mount, not a glyph contribution) — the property
        // admission refuses it (Compose pins the same wire the same way).
        let abspos = member("hyphens__hyphens-out-of-flow-001__3__0",
                            text: "abspos",
                            props: [IRProperty(type: "Position", data: .string("ABSOLUTE"))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "h"),
                   IRRun(child: "hyphens__hyphens-out-of-flow-001__3__0"),
                   IRRun(text: "igh\u{AD}way")],
            children: [abspos], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    // MARK: - Structural gates

    func testUnclaimedChildRefuses() {
        // A plan naming one of TWO children leaves a box that would render
        // AGAIN outside the paragraph — refuse (all-claimed gate).
        let a = member("a", text: "alpha")
        let b = member("b", text: "beta")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [a, b], totalChildCount: 2, containerProperties: [],
            containerLang: nil))
    }

    func testOutOfFlowSiblingRefuses() {
        // totalChildCount counts composed children INCLUDING out-of-flow
        // boxes the in-flow array omits: an abspos sibling's static
        // position depends on the very flow this fold replaces
        // (CSS 2.1 §10.3.7) — refuse until the abspos ring lands.
        let a = member("a", text: "alpha")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [a], totalChildCount: 2, containerProperties: [],
            containerLang: nil))
    }

    func testOutOfOrderAndDuplicateRefsRefuse() {
        // The InlineRunPlan proof, same refusal here: refs must land in
        // strictly increasing rendered order (FlexboxApplier.sorted may
        // permute by CSS `order`), and a duplicate would double glyphs.
        let a = member("a", text: "alpha")
        let b = member("b", text: "beta")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(child: "b"), IRRun(child: "a")],
            children: [a, b], totalChildCount: 2, containerProperties: [],
            containerLang: nil))
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(child: "a"), IRRun(child: "a")],
            children: [a], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    func testDanglingRefRefuses() {
        // A dangling key names glyph content this composition cannot see —
        // a paragraph with a hole must not replace the stacked path.
        let a = member("a", text: "alpha")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(child: "nobody"), IRRun(child: "a")],
            children: [a], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
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
                                             data: .string("VERTICAL_RL"))],
            containerLang: nil))
    }

    func testUnresolvedColorAndDecorationWiresStillRefuse() {
        // Wave 47: a RESOLVED member Color now rides the styled-span ring
        // (see the wave-47 pins below) — but an unresolvable color wire
        // (a bare keyword the converter left raw) still refuses, and a
        // member's own decoration wire attaches to a box the fold erases.
        let colored = member("a", text: "alpha",
                             props: [IRProperty(type: "Color",
                                                data: .string("red"))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [colored], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
        let decorated = member("a", text: "alpha",
                               decorations: [IRDecoration(line: "underline")])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [decorated], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    func testNestedMemberRefuses() {
        // A member with its own children (or runs) is a nested inline
        // TREE — flattening here would guess an order the wire spells out
        // one level down. Deferred until the fold recurses.
        let inner = member("inner", text: "deep")
        let nested = member("a", text: "alpha", children: [inner])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [nested], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
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
            children: [a, b], totalChildCount: 2, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "alpha beta")
    }

    func testBrOnlyHostStaysStackedEquivalent() {
        // Wave 47 (lane Z6): `<br>` members fold to '\n' — but ONLY
        // alongside a glyph member. A {text, <br>}*-only host's stacked
        // fallback already renders one label per anonymous run — exactly
        // the forced-break line structure — and those captures PASS today
        // (block-ellipsis-002: android-ref 0.9884 / ios-ref 0.9813 at
        // wave46-final), so the ring must not move their pixels
        // ("br-stacked-equivalent" — the corpus-simulated rule).
        let br = member("a", tag: "br", text: nil)
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x"), IRRun(child: "a"), IRRun(text: "y")],
            children: [br], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    func testAbsentAndTextOnlyInputsStaySilentNil() {
        // The overwhelming common case (every non-runs component) and the
        // nothing-to-place case: nil, mirroring InlineRunPlan's contract.
        let a = member("a", text: "alpha")
        XCTAssertNil(InlineRunFlow.fold(runs: nil, children: [a],
                                        totalChildCount: 1, containerProperties: [],
                                        containerLang: nil))
        XCTAssertNil(InlineRunFlow.fold(runs: [], children: [a],
                                        totalChildCount: 1, containerProperties: [],
                                        containerLang: nil))
        XCTAssertNil(InlineRunFlow.fold(runs: [IRRun(text: "x")], children: [],
                                        totalChildCount: 0, containerProperties: [],
                                        containerLang: nil))
    }

    func testAllEmptyMergeRefuses() {
        // Every member admitted-and-dropped with no host glyphs: a
        // glyphless fold says nothing the empty container does not — the
        // stacked fallback's empty boxes are the established rendering.
        let em = member("a", tag: "em", text: nil)
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(child: "a")],
            children: [em], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    // MARK: - Wave 47 (lane Z6): the styled-span + br rings

    /// The IR sRGB color leaf shorthand for the wave-47 pins.
    private func srgb(_ r: Double, _ g: Double, _ b: Double) -> IRValue {
        .object(["srgb": .object(["r": .double(r), "g": .double(g), "b": .double(b)])])
    }

    func testBlockEllipsis004ShapeFoldsWithBreaksAndOneStyledSpan() {
        // The wave46-final victim's exact wire shape (ios-ref 0.8997):
        // host runs [text, br, text, br, text, span], the span carrying
        // purple/bold/italic/1.5em + a 2px solid blue border, its OWN
        // runs nesting one more br. The converter emits measured
        // Width/Height on each br (0×0 / 0×20 — the break's box).
        let brProps = [IRProperty(type: "Width", data: .object(["px": .double(0)])),
                       IRProperty(type: "Height", data: .object(["px": .double(20)]))]
        let br0 = member("b0", tag: "br", text: nil, props: brProps)
        let br1 = member("b1", tag: "br", text: nil, props: brProps)
        let innerBr = member("s0", tag: "br", text: nil, props: brProps)
        let span = member("s", text: "Line 3 Line 4",
                          props: [
                              IRProperty(type: "Color", data: srgb(0.5019607843137255, 0, 0.5019607843137255)),
                              IRProperty(type: "FontWeight",
                                         data: .object(["weight": .int(700)])),
                              IRProperty(type: "FontStyle", data: .string("italic")),
                              IRProperty(type: "FontSize",
                                         data: .object(["original": .object([
                                             "type": .string("length"),
                                             "original": .object(["v": .double(1.5), "u": .string("EM")])])])),
                              IRProperty(type: "BorderTopStyle", data: .string("SOLID")),
                              IRProperty(type: "BorderTopWidth", data: .object(["px": .double(2)])),
                              IRProperty(type: "BorderTopColor", data: srgb(0, 0, 1)),
                          ],
                          runs: [IRRun(text: "Line 3"), IRRun(child: "s0"), IRRun(text: " Line 4")],
                          children: [innerBr])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "Line 1"), IRRun(child: "b0"),
                   IRRun(text: " Line 2"), IRRun(child: "b1"),
                   IRRun(text: " "), IRRun(child: "s")],
            children: [br0, br1, span], totalChildCount: 3,
            containerProperties: [IRProperty(type: "Color", data: srgb(0, 0.5, 0.5))],
            containerLang: nil)
        // Four forced-break lines, every collapsible space around a break
        // fallen (css-text-3 §4.1.4) — the exact lines Chromium paints.
        XCTAssertEqual(folded?.text, "Line 1\nLine 2\nLine 3\nLine 4")
        // ONE styled member covering its flattened {text, <br>} subtree.
        XCTAssertEqual(folded?.spans.count, 1)
        guard let span0 = folded?.spans.first, let text = folded?.text else { return }
        let start = text.index(text.startIndex, offsetBy: span0.start)
        let end = text.index(text.startIndex, offsetBy: span0.end)
        XCTAssertEqual(String(text[start..<end]), "Line 3\nLine 4")
        XCTAssertEqual(span0.style.fontWeight, 700)
        XCTAssertTrue(span0.style.italic)
        XCTAssertEqual(span0.style.fontSizeEm, 1.5)
        XCTAssertEqual(span0.style.ink?.r ?? 0, 0.50196, accuracy: 1e-4)
        // The border box is the ring's STATED loss — reported, never silent.
        XCTAssertEqual(span0.statedLossTypes.count, 1)
        XCTAssertTrue(span0.statedLossTypes[0].hasPrefix("border-box-ink("))
    }

    func testInset014ShapeUMemberFoldsWithUnderlineSpan() {
        // text-decoration-inset-014's wire (ios-ref 0.9317): h1 runs
        // ["the ", <u>, " fox"], the u carrying an ink-equal (black)
        // TextDecorationColor + BoxDecorationBreak — with the MERGED host
        // list carrying the inherited black `color` the seam passes.
        let u = member("u1", tag: "u", text: "ultra-quick brown",
                       props: [IRProperty(type: "TextDecorationColor", data: srgb(0, 0, 0)),
                               IRProperty(type: "BoxDecorationBreak", data: .string("CLONE"))])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "the "), IRRun(child: "u1"), IRRun(text: " fox")],
            children: [u], totalChildCount: 1,
            containerProperties: [IRProperty(type: "Color", data: srgb(0, 0, 0))],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "the ultra-quick brown fox")
        // The <u>'s UA underline (HTML rendering §15.3.3) over its range.
        XCTAssertEqual(folded?.spans.count, 1)
        XCTAssertEqual(folded?.spans.first?.style.underline, true)
        XCTAssertEqual(folded?.spans.first?.start, 4)
        XCTAssertEqual(folded?.spans.first?.end, 21)
        // No border → no stated loss.
        XCTAssertEqual(folded?.spans.first?.statedLossTypes.isEmpty, true)
    }

    func testDivergingDecorationColorRefusesTheFold() {
        // inset-011's shape: a blue decoration over black text — the
        // segment underline would paint the wrong color; the whole fold
        // refuses (the member also carries TextUnderlineOffset there,
        // a second named wall this ring does not model).
        let u = member("u1", tag: "u", text: "phrase",
                       props: [IRProperty(type: "TextDecorationColor", data: srgb(0, 0, 1))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "u1")],
            children: [u], totalChildCount: 1,
            containerProperties: [IRProperty(type: "Color", data: srgb(0, 0, 0))],
            containerLang: nil))
    }

    func testBreakSpacesFallOnBothSides() {
        // "a " loses its line-end space, " b " its line-start one
        // (css-text-3 §4.1.4); the glyph member arms the break ring.
        let br = member("b0", tag: "br", text: nil)
        let span = member("s", text: "c")
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "a "), IRRun(child: "b0"),
                   IRRun(text: " b "), IRRun(child: "s")],
            children: [br, span], totalChildCount: 2, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "a\nb c")
        // A plain member carries no attribution — no span recorded.
        XCTAssertEqual(folded?.spans.isEmpty, true)
    }

    func testSmallCapsHostRefusesStyledSpans() {
        // Kept byte-parallel with the Compose twin, whose small-caps
        // synthesis rewrites the string's case outside the alignment's
        // op set (inert here — iOS small-caps is a font feature — but a
        // shared gate keeps the two folds answering identically).
        let span = member("s", text: "y",
                          props: [IRProperty(type: "Color", data: srgb(1, 0, 0))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "s")],
            children: [span], totalChildCount: 1,
            containerProperties: [IRProperty(type: "FontVariantCaps",
                                             data: .string("SMALL_CAPS"))],
            containerLang: nil))
    }

    func testEmptyRunsListMemberFoldsItsOwnText() {
        // An empty-but-present nested runs list ([] survives decode) is
        // NOT a nested tree: the member's own text is the content —
        // routing [] through the flatten would vanish the glyphs.
        let span = member("s", text: "y", runs: [])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "s")],
            children: [span], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "x y")
    }

    func testEmptyRunsListOnBrAndNestedMembersIsNotStructure() {
        // Fix lane F5 (S5 twin hygiene): the BR and depth-2 structure
        // guards read `runs?.isEmpty != false` — an empty-but-present []
        // is NO structure, exactly the Kotlin twin's `isNullOrEmpty`
        // (the pre-fix `== nil` refused these three shapes where the
        // Compose fold admitted them). Zero corpus components carry [];
        // these pins keep the twins from re-diverging.
        // 1) A TOP-LEVEL BR member with runs:[] still folds to '\n'
        //    (classify's BR arm), alongside the glyph member that arms
        //    the break ring.
        let br = member("b0", tag: "br", text: nil, runs: [])
        let span = member("s", text: "c")
        let topLevel = InlineRunFlow.fold(
            runs: [IRRun(text: "a "), IRRun(child: "b0"),
                   IRRun(text: " b "), IRRun(child: "s")],
            children: [br, span], totalChildCount: 2, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(topLevel?.text, "a\nb c")
        // 2) A NESTED BR kid with runs:[] rides the flatten's BR branch.
        let innerBr = member("s0", tag: "br", text: nil, runs: [])
        let host = member("s", text: "y z",
                          runs: [IRRun(text: "y"), IRRun(child: "s0"), IRRun(text: "z")],
                          children: [innerBr])
        let nestedBr = InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "s")],
            children: [host], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(nestedBr?.text, "x y\nz")
        // 3) A NESTED GLYPH kid with runs:[] passes the depth-2 wall
        //    (it has no structure) and folds its own text.
        let innerSup = member("inner", tag: "sup", text: "2", runs: [])
        let outer = member("s", tag: "i", text: "e = mc2",
                           runs: [IRRun(text: "e = mc"), IRRun(child: "inner")],
                           children: [innerSup])
        let nestedGlyph = InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "s")],
            children: [outer], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(nestedGlyph?.text, "x e = mc2")
        // The composed sup piece still carries its shift — [] changed
        // structure classification only, never the admitted attribution.
        XCTAssertEqual(nestedGlyph?.spans.last?.style.shift?.up, true)
    }

    func testNestedForeignTagDepthTwoAndUnclaimedNestedChildRefuse() {
        // Wave 48 admits one-level nested GLYPH members through the span
        // ring — a nested tag OUTSIDE the ring (`rt`, ruby semantics no
        // span expresses) keeps the wall…
        let innerRt = member("inner", tag: "rt", text: "deep")
        let nested = member("s", text: "y deep",
                            runs: [IRRun(text: "y "), IRRun(child: "inner")],
                            children: [innerRt])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "s")],
            children: [nested], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
        // …a nested member with its OWN structure is the depth-2 wall
        // (decorating-box-001-class trees keep their frozen path)…
        let leaf = member("leaf", tag: "sup", text: "w")
        let innerDeep = member("inner", text: "z w",
                               runs: [IRRun(text: "z "), IRRun(child: "leaf")],
                               children: [leaf])
        let deep = member("s", text: "y z w",
                          runs: [IRRun(text: "y "), IRRun(child: "inner")],
                          children: [innerDeep])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "s")],
            children: [deep], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
        // …and an unclaimed nested child would silently vanish (the
        // outer fold consumes the whole member) — refused.
        let innerBr = member("b0", tag: "br", text: nil)
        let unclaimed = member("s", text: "y",
                               runs: [IRRun(text: "y")],
                               children: [innerBr])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "s")],
            children: [unclaimed], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
    }

    func testPlainNestedSpanFoldsFlat() {
        // Wave 48 (lane W4): the shape wave 47 pinned as the nested wall
        // now folds — a plain nested span carries no attribution, so the
        // paragraph is uniform and span-less.
        let inner = member("inner", text: "deep")
        let nested = member("s", text: "y deep",
                            runs: [IRRun(text: "y "), IRRun(child: "inner")],
                            children: [inner])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "s")],
            children: [nested], totalChildCount: 1, containerProperties: [],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "x y deep")
        XCTAssertEqual(folded?.spans.isEmpty, true)
    }

    // MARK: - Wave 48 (lane W4) — the UA-styled + nested-glyph rings

    func testSubelements002FirstHostFoldsItalicAndSupPieces() {
        // wpt__css-text-decor__text-decoration-subelements-002, first
        // `div.test` host (wave48-cal android-ref 0.7649 / ios-ref
        // 0.7272 — ten stacked blocks where Chromium flows two lines):
        // runs [text "Einstein said that ", <i>, text "."], the i's own
        // runs [text "e = mc", <sup>2</sup>] — VERBATIM shapes from
        // tools/titan/runs/wave48-cal/sections/css-text-decor/per-test-ir.
        let sup = member("text-decoration-subelements-002__1__0__0__0__0",
                         tag: "sup", text: "2")
        let i = member("text-decoration-subelements-002__1__0__0__0",
                       tag: "i", text: "e = mc",
                       runs: [IRRun(text: "e = mc"),
                              IRRun(child: "text-decoration-subelements-002__1__0__0__0__0")],
                       children: [sup])
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "Einstein said that "),
                   IRRun(child: "text-decoration-subelements-002__1__0__0__0"),
                   IRRun(text: ".")],
            children: [i], totalChildCount: 1,
            // The host's own wire: underline + abspos + nowrap — all
            // HOST-level (the paragraph pipeline owns them, no gate).
            containerProperties: [
                IRProperty(type: "TextDecorationLine", data: .array([.string("UNDERLINE")])),
                IRProperty(type: "Position", data: .string("ABSOLUTE")),
                IRProperty(type: "WhiteSpace", data: .string("NOWRAP")),
            ],
            containerLang: nil)
        // ONE flowing line, the ref's exact paint order.
        XCTAssertEqual(folded?.text, "Einstein said that e = mc2.")
        // Two pieces: the i's own glyphs (UA italic) and the nested sup
        // (italic inherited + UA smaller + the super shift).
        XCTAssertEqual(folded?.spans.count, 2)
        guard let spans = folded?.spans, spans.count == 2 else { return }
        XCTAssertEqual(spans[0].start, 19)
        XCTAssertEqual(spans[0].end, 25)
        XCTAssertTrue(spans[0].style.italic)
        XCTAssertNil(spans[0].style.shift)
        XCTAssertEqual(spans[1].start, 25)
        XCTAssertEqual(spans[1].end, 26)
        XCTAssertTrue(spans[1].style.italic)
        XCTAssertEqual(spans[1].style.fontSizeEm.map { Double($0) } ?? 0, 1.0 / 1.2, accuracy: 1e-6)
        XCTAssertEqual(spans[1].style.shift,
                       InlineSpanRing.VerticalShift(up: true, parentPx: nil, parentEm: 1))
    }

    func testInset005UHostFoldsDirectSupAndSubShifts() {
        // wpt__css-text-decor__text-decoration-inset-005's `<u>` runs
        // host (wave48-cal android-ref 0.913 / ios-ref 0.915 — five
        // stacked blocks): runs [text "ultra-", <sup>quick</sup>,
        // text " b", <sub>row</sub>, text "n"], the u's own
        // TextDecorationColor being a HOST property here (ungated).
        let sup = member("text-decoration-inset-005__1__0__0__0",
                         tag: "sup", text: "quick")
        let sub = member("text-decoration-inset-005__1__0__0__1",
                         tag: "sub", text: "row")
        let folded = InlineRunFlow.fold(
            runs: [IRRun(text: "ultra-"),
                   IRRun(child: "text-decoration-inset-005__1__0__0__0"),
                   IRRun(text: " b"),
                   IRRun(child: "text-decoration-inset-005__1__0__0__1"),
                   IRRun(text: "n")],
            children: [sup, sub], totalChildCount: 2,
            containerProperties: [
                IRProperty(type: "TextDecorationColor",
                           data: .object(["srgb": .object(["r": .int(0), "g": .int(0), "b": .int(0)]),
                                          "original": .string("black")])),
            ],
            containerLang: nil)
        XCTAssertEqual(folded?.text, "ultra-quick brown")
        XCTAssertEqual(folded?.spans.count, 2)
        guard let spans = folded?.spans, spans.count == 2 else { return }
        // "quick" raised, "row" lowered — both one UA `smaller` step.
        XCTAssertEqual(spans[0].style.shift,
                       InlineSpanRing.VerticalShift(up: true, parentPx: nil, parentEm: 1))
        XCTAssertEqual(spans[1].style.shift,
                       InlineSpanRing.VerticalShift(up: false, parentPx: nil, parentEm: 1))
        XCTAssertEqual(spans[0].style.fontSizeEm.map { Double($0) } ?? 0, 1.0 / 1.2, accuracy: 1e-6)
        XCTAssertFalse(spans[0].style.italic)
    }
}
