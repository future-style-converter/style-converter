//
//  UABlockMarginFontBasisTests.swift
//  StyleConverterRuntimeTests
//
//  wave-46 lane H1 (Y8's iOS twin) — the B1-B8 / F pins for
//  UABlockMarginFontBasis, the font basis of the UA default block margins,
//  plus the U13-U15 child-fold pins and the Y1-Y3 root-stack pins. Every
//  expected value is IDENTICAL to the Kotlin twins
//  (runtime/spacing/UaBlockMarginFontBasisTest.kt, UaBlockChildMarginsTest
//  U13-U15, apps/android-harness UaBlockMarginsTest Y1-Y3) so the two
//  natives cannot drift.
//
//  Provenance: CSS2/cascade/inherit-computed-001 on wave45-final. The
//  composed root `<p>` declares `font-size: larger` (live wire copied
//  verbatim below from tools/titan/runs/wave45-final/sections/CSS2/
//  per-test-ir/wpt__CSS2__cascade__inherit-computed-001.json); the
//  Chromium browser-ref puts its border-top at row 35 = 16px image pad +
//  19.2px (1em of the p's OWN computed 19.2px), both natives at row 32 =
//  16 + the fixed 16px-root table value (iOS border rows 32-34 vs the
//  ref's 35-37). Translating the iOS capture down 3px re-scores 0.8863 →
//  0.9767 through the scorer's own diffWebVsRef — the basis IS the
//  residual. These pins hold that number and, above all, the IDENTITY
//  contract: every element without its own font signal must resolve to nil
//  so the table is kept verbatim.
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class UABlockMarginFontBasisTests: XCTestCase {

    // MARK: - IR helpers (same wire-decoding pattern as UABlockMarginTests)

    /// One IR property from a wire JSON literal (the frozen byte shape) —
    /// IRProperty is Decodable-only, so the pins ride converter bytes.
    private func prop(_ type: String, _ json: String) -> IRProperty {
        try! JSONDecoder().decode(IRProperty.self,
                                  from: Data("{\"type\":\"\(type)\",\"data\":\(json)}".utf8))
    }

    /// The inherit-computed-001 wire, verbatim from the wave45-final per-test IR.
    private var larger: IRProperty {
        prop("FontSize", #"{"original":{"type":"relative","keyword":"larger"}}"#)
    }

    /// Floating tolerance for the ×1.2 products.
    private let eps: CGFloat = 1e-4

    // MARK: - B1/B2: the identity contract

    /// B2 — NO FontSize and no monospace family ⇒ nil: the caller keeps
    /// the 16px-root table, which is what protects every corpus root and
    /// child without a font-size declaration (the simulated blast radius
    /// of the root path is exactly ONE test).
    func testB2_noOwnFontSignalResolvesToNil() {
        XCTAssertNil(UABlockMarginFontBasis.ownFontSizePx([]))
        XCTAssertNil(UABlockMarginFontBasis.ownFontSizePx([prop("MarginTop", #"{"px":20}"#)]))
        // A non-monospace family is not a font-SIZE signal either.
        XCTAssertNil(UABlockMarginFontBasis.ownFontSizePx([prop("FontFamily", #"["Inter"]"#)]))
    }

    /// B1 — no FontSize but a first-family `monospace` ⇒ Chromium's 13px
    /// defaultFixedFontSize via MonospaceUAFontSize (consulted, not
    /// re-derived): a `<p style="font-family: monospace">` has a 13px UA
    /// margin in the browser.
    func testB1_monospaceQuirkIsTheBasisWhenNothingIsDeclared() {
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx([prop("FontFamily", #"["monospace"]"#)]), 13)
    }

    /// B1-gate — the fixed default is a KEYWORD-size rule. Blink resolves
    /// `medium` against the fixed table only when the computed size CAME
    /// from a keyword; an element whose UA size is an em (html.css h1 2em,
    /// h2 1.5em, h3 1.17em, h5 .83em, h6 .67em — h4 declares none) takes
    /// CheckForGenericFamilyChange's 13/16 scaling instead, so a monospace
    /// `<h1>` computes 26px and its UA margin is .67 × 26 ≈ 17.4px. An
    /// unguarded B1 gave .67 × 13 = 8.71px — a 12px error. This module does
    /// not model the scaling: the em-sized headings resolve to nil and keep
    /// the ref-calibrated table (h1 → 21px), which is the pre-wave-46 render
    /// and the right answer for any non-monospace heading. Same expected
    /// values as the Kotlin twin's `B1 gate` pin.
    func testB1Gate_emSizedHeadingNeverTakesTheMonospaceFixedDefault() {
        let mono = [prop("FontFamily", #"["monospace"]"#)]
        for t in ["h1", "h2", "h3", "h5", "h6", "H1"] {
            XCTAssertNil(UABlockMarginFontBasis.ownFontSizePx(mono, sourceTag: t), "tag=\(t)")
        }
        // The keyword-sized tags keep the quirk — as does a tag-less caller
        // (identity for every pre-wave-46 call site) and any tag outside the
        // UA margin table, whose basis cannot move a pixel anyway.
        for t in ["p", "ul", "ol", "blockquote", "figure", "pre", "h4", "div", nil] {
            XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx(mono, sourceTag: t), 13,
                           "tag=\(String(describing: t))")
        }
        // The margin the gate protects, end to end: the table's 21px, NOT
        // the .67 × 13 = 8.71px an unguarded B1 produced (and not the
        // browser's ≈17.4 either — the conservative fallback, stated).
        let h1 = UABlockMargin.rootVertical(
            forTag: "h1", ownFontSizePx: UABlockMarginFontBasis.ownFontSizePx(mono, sourceTag: "h1"))
        XCTAssertEqual(h1.top, 21); XCTAssertEqual(h1.bottom, 21)
        XCTAssertNotEqual(h1.top, 0.67 * 13)
        // A DECLARED size is tag-independent: nothing is left for the UA
        // sheet to scale, so an `<h1>` with font-size: 40px still resolves 40.
        let declared = [prop("FontSize", #"{"px":40,"original":{"type":"length","px":40}}"#)]
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx(declared, sourceTag: "h1"), 40)
    }

    // MARK: - B3-B7: the resolution ladder

    /// B3 — a resolved top-level px wins outright (absolute lengths and
    /// DynamicValueResolver prebakes both ride this shape).
    func testB3_topLevelPxIsTheBasis() {
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx([
            prop("FontSize", #"{"px":92,"original":{"type":"length","px":92}}"#)]), 92)
        // Zero / negative cannot scale a margin ⇒ nil (table kept).
        XCTAssertNil(UABlockMarginFontBasis.ownFontSizePx([prop("FontSize", #"{"px":0}"#)]))
    }

    /// B4 — absolute keywords resolve through the css-fonts-4 §2.5.1 ladder.
    func testB4_absoluteKeywordLadder() {
        func kw(_ k: String) -> [IRProperty] {
            [prop("FontSize", #"{"original":{"type":"absolute","keyword":"\#(k)"}}"#)]
        }
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx(kw("xx-small")), 9)
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx(kw("medium")), 16)
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx(kw("xxx-large")), 48)
        XCTAssertNil(UABlockMarginFontBasis.ownFontSizePx(kw("huge")))
    }

    /// B5 — THE measured case: `larger` of the inherited 16px = 19.2px
    /// (CSS 2.1 §15.7 one ladder step, Blink's ×1.2), `smaller` = 13.33.
    func testB5_largerAndSmallerStepFromTheInheritedSize() {
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx([larger])!, 19.2, accuracy: eps)
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx([
            prop("FontSize", #"{"original":{"type":"relative","keyword":"smaller"}}"#)])!,
            16 / 1.2, accuracy: eps)
        // The inherited base threads through: larger of a 20px parent = 24.
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx([larger], inheritedPx: 20)!, 24, accuracy: eps)
    }

    /// B6/B7 — em and % on font-size resolve against the INHERITED size
    /// (css-values-4 §6.1.1 / css-fonts-4 §2.5); rem against the 16px root.
    func testB6B7_emRemAndPercentage() {
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx([
            prop("FontSize", #"{"original":{"type":"length","original":{"v":2,"u":"EM"}}}"#)])!,
            32, accuracy: eps)
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx([
            prop("FontSize", #"{"original":{"type":"length","original":{"v":1.5,"u":"REM"}}}"#)],
            inheritedPx: 30)!, 24, accuracy: eps)
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx([
            prop("FontSize", #"{"original":{"type":"percentage","value":120}}"#)])!,
            19.2, accuracy: eps)
    }

    /// B8 — declared but not statically resolvable (var()/calc()/vw) ⇒
    /// nil, the documented honest fallback to the table.
    func testB8_unresolvableDeclaredSizesFallBackToNil() {
        XCTAssertNil(UABlockMarginFontBasis.ownFontSizePx([
            prop("FontSize", #"{"original":{"type":"expression","expr":"calc(1em + 2px)"}}"#)]))
        XCTAssertNil(UABlockMarginFontBasis.ownFontSizePx([
            prop("FontSize", #"{"original":{"type":"length","original":{"v":5,"u":"VW"}}}"#)]))
        XCTAssertNil(UABlockMarginFontBasis.ownFontSizePx([prop("FontSize", #""var(--x)""#)]))
    }

    /// Last declaration wins, mirroring the extractors' fold.
    func testLastFontSizeDeclarationWins() {
        XCTAssertEqual(UABlockMarginFontBasis.ownFontSizePx([
            prop("FontSize", #"{"px":10}"#), prop("FontSize", #"{"px":20}"#)]), 20)
    }

    // MARK: - F: parity with the painted size

    /// F — the basis the UA margin resolves against must be the size the
    /// glyphs actually paint at (DynamicValueResolver.resolveFontSizeWire's
    /// ladder against the 16px root default — the path ComponentRenderer's
    /// pass 0 takes for a composed root), or the margin and the text would
    /// disagree about the em. Pinned for the relative keyword, the em shape
    /// and the percentage; the px shape against ValueExtractors.extractPx.
    func testF_basisEqualsThePaintedFontSize() {
        for p in [
            larger,
            prop("FontSize", #"{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}"#),
            prop("FontSize", #"{"original":{"type":"percentage","value":75}}"#),
            prop("FontSize", #"{"original":{"type":"relative","keyword":"smaller"}}"#),
        ] {
            let painted = DynamicValueResolver.resolveFontSizeWire(p.data, inheritedFontSizePx: 16)
            XCTAssertNotNil(painted, "\(p.data)")
            XCTAssertEqual(CGFloat(painted!), UABlockMarginFontBasis.ownFontSizePx([p])!,
                           accuracy: eps, "\(p.data)")
        }
        // The absolute px lane: the same top-level px every extractor reads.
        let px = prop("FontSize", #"{"px":24,"original":{"type":"absolute","keyword":"x-large"}}"#)
        XCTAssertEqual(ValueExtractors.extractPx(px.data), UABlockMarginFontBasis.ownFontSizePx([px]))
    }

    // MARK: - The em factor table

    /// The UA sheet's block-margin em per tag (Chromium html.css).
    func testUaBlockMarginEmFactors() {
        for t in ["p", "ul", "ol", "blockquote", "pre", "figure", "h3", "P"] {
            XCTAssertEqual(UABlockMarginFontBasis.uaBlockMarginEm(forTag: t), 1, "tag=\(t)")
        }
        XCTAssertEqual(UABlockMarginFontBasis.uaBlockMarginEm(forTag: "h1"), 0.67)
        XCTAssertEqual(UABlockMarginFontBasis.uaBlockMarginEm(forTag: "h6"), 2.33)
        for t in ["div", "li", "span", "section", nil] {
            XCTAssertNil(UABlockMarginFontBasis.uaBlockMarginEm(forTag: t), "tag=\(String(describing: t))")
        }
    }

    // MARK: - U13-U15: the own-font basis through the child table/merge

    /// U13 — a NIL basis is the identity: the two-arg table equals the
    /// one-arg table for every tag, which is the contract that keeps every
    /// corpus element without a font-size declaration byte-identical.
    func testU13_nilBasisIsThe16pxRootTableVerbatim() {
        for tag in ["p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol",
                    "blockquote", "pre", "figure", "div", "li", nil] {
            let one = UABlockMargin.vertical(forTag: tag)
            let two = UABlockMargin.vertical(forTag: tag, ownFontSizePx: nil)
            XCTAssertEqual(one.top, two.top, "tag=\(String(describing: tag)) top")
            XCTAssertEqual(one.bottom, two.bottom, "tag=\(String(describing: tag)) bottom")
            // And the rounded ROOT table is the same identity there.
            let root = UABlockMargin.rootVertical(forTag: tag, ownFontSizePx: nil)
            XCTAssertEqual(one.top, root.top, "tag=\(String(describing: tag)) root")
        }
        // The default parameter on the merge is the same identity.
        let merged = UABlockMargin.childBlockEdges(tag: "p", properties: [],
                                                   declared: (0, 0), enabled: true)
        XCTAssertEqual(merged.top, 16); XCTAssertEqual(merged.bottom, 16)
    }

    /// U14 — THE measured case: a `<p>` whose own computed size is 19.2px
    /// (`font-size: larger` of 16, inherit-computed-001) carries 1em ×
    /// 19.2 = 19.2px on both edges — the browser-ref's row 35 (16 pad +
    /// 19.2) against the 16px table's row 32. A 1em tag scales linearly
    /// (a 92px `<p>` → 92), and a font-sized `<div>` still has none.
    func testU14_1emTagsResolveAgainstTheOwnFontBasis() {
        let p = UABlockMargin.vertical(forTag: "p", ownFontSizePx: 19.2)
        XCTAssertEqual(p.top, 19.2, accuracy: eps); XCTAssertEqual(p.bottom, 19.2, accuracy: eps)
        XCTAssertEqual(UABlockMargin.vertical(forTag: "ul", ownFontSizePx: 92).top, 92)
        XCTAssertEqual(UABlockMargin.vertical(forTag: "blockquote", ownFontSizePx: 13).top, 13)
        XCTAssertEqual(UABlockMargin.vertical(forTag: "div", ownFontSizePx: 19.2).top, 0)
        // Through the merge: declared edges still win per edge (§6.1).
        let bare = UABlockMargin.childBlockEdges(tag: "p", properties: [], declared: (0, 0),
                                                 enabled: true, ownFontSizePx: 19.2)
        XCTAssertEqual(bare.top, 19.2, accuracy: eps); XCTAssertEqual(bare.bottom, 19.2, accuracy: eps)
        let topDeclared = UABlockMargin.childBlockEdges(
            tag: "p", properties: [prop("MarginTop", #"{"type":"length","px":40.0}"#)],
            declared: (40, 0), enabled: true, ownFontSizePx: 19.2)
        XCTAssertEqual(topDeclared.top, 40); XCTAssertEqual(topDeclared.bottom, 19.2, accuracy: eps)
        // And the dark-stage flag still short-circuits BEFORE the basis.
        let off = UABlockMargin.childBlockEdges(tag: "p", properties: [], declared: (0, 0),
                                                enabled: false, ownFontSizePx: 19.2)
        XCTAssertEqual(off.top, 0); XCTAssertEqual(off.bottom, 0)
    }

    /// U15 — headings scale by their OWN em factor over the given basis
    /// (html.css `h1 { margin-block: .67em }` … `h6 { 2.33em }`): an `<h1>`
    /// the author resized to 40px carries .67 × 40 = 26.8px.
    func testU15_headingsScaleByTheirUaEmFactor() {
        let h1 = UABlockMargin.vertical(forTag: "h1", ownFontSizePx: 40)
        XCTAssertEqual(h1.top, 26.8, accuracy: eps); XCTAssertEqual(h1.bottom, 26.8, accuracy: eps)
        XCTAssertEqual(UABlockMargin.vertical(forTag: "h6", ownFontSizePx: 10).top, 23.3, accuracy: eps)
    }

    // MARK: - Y1-Y3: the composed ROOT path (the harness twins)

    /// The inherit-computed-001 root: `<p>` + `font-size: larger`, no margins.
    private var largerParagraph: [IRProperty] { [larger] }

    /// Y1: 1em × 19.2 = 19.2 → 19 whole px (the canvas pad is placed at
    /// integer px; 16 + 19 = row 35, the ref's row). Both edges; R1 shape
    /// (no declared block margin, never strips).
    func testY1_largerParagraphRootUaMarginIs19px() {
        let ua = UABlockMargin.rootVertical(forTag: "p", ownFontSizePx: 19.2)
        XCTAssertEqual(ua.top, 19); XCTAssertEqual(ua.bottom, 19)
        let eff = UABlockMargin.effectiveVertical(
            tag: "p", properties: largerParagraph,
            ownFontSizePx: UABlockMarginFontBasis.ownFontSizePx(largerParagraph))
        XCTAssertEqual(eff.top, 19); XCTAssertEqual(eff.bottom, 19)
        // The root-stack plan the rootPlans fold builds for this root.
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: false, declaresBottom: false,
            staticDeclaredEdges: (top: 0, bottom: 0),
            ownFontSizePx: UABlockMarginFontBasis.ownFontSizePx(largerParagraph))
        XCTAssertEqual(plan, .init(top: 19, bottom: 19, stripDeclared: false))
    }

    /// Y2: every root WITHOUT an own font signal resolves a nil basis and
    /// keeps the Round-4 numbers — the identity that protects the whole
    /// corpus (simulated root blast radius: exactly one test).
    func testY2_nilBasisIsTheTableVerbatim() {
        let plain = UABlockMargin.effectiveVertical(
            tag: "p", properties: [], ownFontSizePx: UABlockMarginFontBasis.ownFontSizePx([]))
        XCTAssertEqual(plain.top, 16); XCTAssertEqual(plain.bottom, 16)
        let h2 = UABlockMargin.rootVertical(forTag: "h2", ownFontSizePx: nil)
        XCTAssertEqual(h2.top, UABlockMargin.vertical(forTag: "h2").top)
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: false, declaresBottom: false,
            staticDeclaredEdges: (top: 0, bottom: 0), ownFontSizePx: nil)
        XCTAssertEqual(plan, .init(top: 16, bottom: 16, stripDeclared: false))
    }

    /// Y3: author > UA per edge (css-cascade-4 §6.1) survives the basis —
    /// a declared top zeroes the UA top, the scaled UA bottom stays; and
    /// through the stack plan the declared px replaces it (R3 shape).
    func testY3_declaredSideStillWinsOverTheScaledUa() {
        let eff = UABlockMargin.effectiveVertical(
            tag: "p", properties: [prop("MarginTop", #"{"type":"length","px":5.0}"#)],
            ownFontSizePx: 19.2)
        XCTAssertEqual(eff.top, 0); XCTAssertEqual(eff.bottom, 19)
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: true, declaresBottom: false,
            staticDeclaredEdges: (top: 20, bottom: 0), ownFontSizePx: 19.2)
        XCTAssertEqual(plan, .init(top: 20, bottom: 19, stripDeclared: true))
    }
}
