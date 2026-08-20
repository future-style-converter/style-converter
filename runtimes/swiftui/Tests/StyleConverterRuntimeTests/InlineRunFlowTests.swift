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
        // carrying only colors folds; one carrying a STYLE bails.
        let inert = member("a", text: "alpha",
                           props: [IRProperty(type: "BorderTopColor",
                                              data: .object(["original": .string("red")]))])
        XCTAssertEqual(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [inert], totalChildCount: 1, containerProperties: [],
            containerLang: nil)?.text, "x alpha")
        // The style makes the border paint — a real box the fold erases.
        let styled = member("a", text: "alpha",
                            props: [IRProperty(type: "BorderTopColor",
                                               data: .object(["original": .string("red")])),
                                    IRProperty(type: "BorderTopStyle",
                                               data: .string("SOLID"))])
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [styled], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
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
        // The EMPTY ring's wider tags admit only GLYPHLESS members: an
        // `<em>` WITH text would fold without its UA italic (a producer
        // gap the fold must not bake in) — tag-gated out, as before.
        let em = member("a", tag: "em", text: "emphasised")
        XCTAssertNil(InlineRunFlow.fold(
            runs: [IRRun(text: "x "), IRRun(child: "a")],
            children: [em], totalChildCount: 1, containerProperties: [],
            containerLang: nil))
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

    func testMemberStyleAndDecorationWiresRefuse() {
        // A TEXT member's own Color is the styled-span ring (folding would
        // ink it with the parent's color); a member's own decoration wire
        // attaches to a box the fold erases. Both refuse.
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

    func testBrMemberRefusesOnItsOwnNamedWall() {
        // `<br>` is a FORCED break, not an atom — a real "\n" candidate
        // for a future ring, refused today (123 references at wave43-final
        // are unstudied pixel-wise). This pin documents the wall so
        // engaging it later is a deliberate flip, not drift.
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
}
