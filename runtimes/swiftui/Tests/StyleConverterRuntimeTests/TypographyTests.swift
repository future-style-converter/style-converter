//
//  TypographyTests.swift
//  StyleConverterRuntimeTests
//
//  XCTest port of the Phase 6 typography launch self-test (formerly
//  StyleEngine/typography/TypographySelfTest.swift): every rendering-
//  capable triplet under StyleEngine/typography/{font,font-variant,
//  line,spacing,decoration,wrapping,writing,other} plus spot-checks on
//  the five grouped "unsupported" extractors. Check bodies unchanged —
//  every failure-collection site survives 1:1; the print-PASS/FAIL
//  summary becomes one XCTFail per collected failure name.
//

import Foundation
import SwiftUI
// UIKit for the synthetic-oblique matrix pin (UIFont/UIFontDescriptor).
import UIKit
import XCTest
// @testable: the Phase 6 extractors are internal to the runtime module.
@testable import StyleConverterRuntime

final class TypographyTests: XCTestCase {

    // One test per section of the original self-test.
    func testFontChecks()        { for failure in Self.runFontChecks()        { XCTFail(failure) } }
    func testFontVariantChecks() { for failure in Self.runFontVariantChecks() { XCTFail(failure) } }
    func testLineChecks()        { for failure in Self.runLineChecks()        { XCTFail(failure) } }
    func testSpacingChecks()     { for failure in Self.runSpacingChecks()     { XCTFail(failure) } }
    func testDecorationChecks()  { for failure in Self.runDecorationChecks()  { XCTFail(failure) } }
    func testWrappingChecks()    { for failure in Self.runWrappingChecks()    { XCTFail(failure) } }
    func testWritingChecks()     { for failure in Self.runWritingChecks()     { XCTFail(failure) } }
    func testOtherChecks()       { for failure in Self.runOtherChecks()       { XCTFail(failure) } }
    func testUnsupportedChecks() { for failure in Self.runUnsupportedChecks() { XCTFail(failure) } }
    func testAggregateChecks()   { for failure in Self.runAggregateChecks()   { XCTFail(failure) } }

    // MARK: - Helpers (shared shape with the other SelfTest modules)

    private static func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private static func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }
    private static func srgb(_ r: Double, _ g: Double, _ b: Double, _ a: Double? = nil) -> IRValue {
        var dict: [String: IRValue] = [
            "r": .double(r), "g": .double(g), "b": .double(b),
        ]
        if let a = a { dict["a"] = .double(a) }
        return obj(["srgb": obj(dict)])
    }

    // MARK: - font/

    private static func runFontChecks() -> [String] {
        var f: [String] = []
        // FontSize
        if FontSizeExtractor.extract(from: props([("FontSize", obj(["px": .double(18)]))]))?.px != 18 {
            f.append("FontSize: 18pt round-trip")
        }
        // FontWeight keyword + number
        if FontWeightExtractor.extract(from: props([("FontWeight", .string("bold"))]))?.weight != .bold {
            f.append("FontWeight: `bold` → .bold")
        }
        if FontWeightExtractor.extract(from: props([("FontWeight", .int(700))]))?.weight != .bold {
            f.append("FontWeight: 700 → .bold bucket")
        }
        // FontStyle italic
        if FontStyleExtractor.extract(from: props([("FontStyle", .string("italic"))]))?.italic != true {
            f.append("FontStyle: italic → true")
        }
        // FontStyle oblique — wave 6 (lane FONT-STYLE-OBLIQUE). Wire shapes
        // byte-copied from a live converter run on fixtures/properties/
        // typography/font-style.json: keywords are bare strings; `oblique
        // <angle>` is {"oblique":{"deg":N,…}} with deg pre-normalized and
        // an optional "original" {v,u} echo for non-deg source units.
        // Expected italic flag mirrors the wave-6 web reference captures:
        // Chromium's synthetic oblique engages at 14deg (css-fonts-4 §2.5
        // default oblique angle) — 14deg/18deg == italic pixel-identical,
        // 0deg/-10deg/11.46deg == normal pixel-identical.
        if FontStyleExtractor.extract(from: props([("FontStyle", .string("oblique"))]))?.italic != true {
            f.append("FontStyle: bare oblique (= 14deg default) → true")
        }
        if FontStyleExtractor.extract(from: props([
            ("FontStyle", obj(["oblique": obj(["deg": .double(14.0)])])),
        ]))?.italic != true {
            f.append("FontStyle: oblique 14deg object → true")
        }
        if FontStyleExtractor.extract(from: props([
            ("FontStyle", obj(["oblique": obj(["deg": .double(0.0)])])),
        ]))?.italic != false {
            f.append("FontStyle: oblique 0deg → false")
        }
        if FontStyleExtractor.extract(from: props([
            ("FontStyle", obj(["oblique": obj(["deg": .double(-10.0)])])),
        ]))?.italic != false {
            f.append("FontStyle: oblique -10deg → false")
        }
        // `oblique 0.2rad` — the converted-unit wire keeps an "original"
        // sibling the extractor must tolerate; 11.459deg < 14 → upright.
        if FontStyleExtractor.extract(from: props([
            ("FontStyle", obj(["oblique": obj([
                "deg": .double(11.459155902616466),
                "original": obj(["v": .double(0.2), "u": .string("RAD")]),
            ])])),
        ]))?.italic != false {
            f.append("FontStyle: oblique 0.2rad (11.46deg) → false")
        }
        // `oblique 0.05turn` → 18deg ≥ 14 → italic appearance.
        if FontStyleExtractor.extract(from: props([
            ("FontStyle", obj(["oblique": obj([
                "deg": .double(18.0),
                "original": obj(["v": .double(0.05), "u": .string("TURN")]),
            ])])),
        ]))?.italic != true {
            f.append("FontStyle: oblique 0.05turn (18deg) → true")
        }
        // Malformed {"oblique":{}} — no numeric "deg". Unreachable from the
        // fixed converter wire (FontStylePropertyParser.kt now rejects the
        // whole declaration on an unparseable angle), but pinned anyway:
        // drop the payload — italic stays nil (= inherit/upright, the
        // FontStyleConfig "null"), matching Compose's convention
        // (TextStyleApplier.extractFontStyle returns null) — never guess
        // the 14deg bare-oblique default.
        if FontStyleExtractor.extract(from: props([
            ("FontStyle", obj(["oblique": obj([:])])),
        ]))?.italic != nil {
            f.append("FontStyle: malformed oblique {} → nil drop, no 14deg guess")
        }
        // Synthetic-oblique skew math (render vehicle for the flags above):
        // the shear matrix must be the fixed 0.25 slope Blink/Android
        // synthesize (Skia textSkewX -0.25; UIKit y-up → POSITIVE c leans
        // right), and the point size must survive the descriptor rebuild.
        let roman = UIFont.systemFont(ofSize: 22)
        let slanted = syntheticObliqueUIFont(roman)
        // Read the matrix through Core Text — UIFontDescriptor.matrix is
        // marked unavailable under Mac Catalyst, but the CTFont bridge
        // (the exact type the render path hands SwiftUI) exposes it.
        let m = CTFontGetMatrix(slanted as CTFont)
        if abs(m.c - 0.25) > 1e-9 || m.a != 1 || m.b != 0 || m.d != 1 {
            f.append("syntheticOblique: matrix must be pure c=0.25 shear, got \(m)")
        }
        if slanted.pointSize != 22 {
            f.append("syntheticOblique: point size must survive (got \(slanted.pointSize))")
        }
        // FontFamily name + generic flag
        let ff = FontFamilyExtractor.extract(from: props([("FontFamily", .array([
            obj(["name": .string("Helvetica")]),
            obj(["keyword": .string("sans-serif")]),
            obj(["keyword": .string("monospace")]),
        ]))]))
        if ff?.names.first != "Helvetica" { f.append("FontFamily: first name") }
        if ff?.hasMonospace != true      { f.append("FontFamily: monospace flag") }
        // FontStretch keyword → percent
        if FontStretchExtractor.extract(from: props([("FontStretch", .string("condensed"))]))?.percent != 75 {
            f.append("FontStretch: `condensed` → 75%")
        }
        // FontFeatureSettings list round-trip
        let feat = FontFeatureSettingsExtractor.extract(from: props([("FontFeatureSettings", .array([
            obj(["tag": .string("smcp"), "value": .int(1)]),
        ]))]))
        if feat?.features.first?.tag != "smcp" { f.append("FontFeatureSettings: tag round-trip") }
        // FontVariationSettings axis list round-trip
        let varset = FontVariationSettingsExtractor.extract(from: props([("FontVariationSettings", .array([
            obj(["tag": .string("wght"), "value": .double(650)]),
        ]))]))
        if varset?.axes.first?.value != 650 { f.append("FontVariationSettings: axis value") }
        // FontKerning none
        if FontKerningExtractor.extract(from: props([("FontKerning", .string("none"))]))?.mode != FontKerningMode.none {
            f.append("FontKerning: none")
        }
        // FontOpticalSizing auto
        if FontOpticalSizingExtractor.extract(from: props([("FontOpticalSizing", .string("auto"))]))?.mode != .auto {
            f.append("FontOpticalSizing: auto")
        }
        return f
    }

    // MARK: - font-variant/

    private static func runFontVariantChecks() -> [String] {
        var f: [String] = []
        // Caps: small-caps flag
        if FontVariantCapsExtractor.extract(from: props([("FontVariantCaps", .string("small-caps"))]))?.mode != .smallCaps {
            f.append("FontVariantCaps: small-caps")
        }
        // Numeric: ligatures keyword list
        let n = FontVariantNumericExtractor.extract(from: props([
            ("FontVariantNumeric", .string("tabular-nums lining-nums")),
        ]))
        if n?.keywords.contains("tabular-nums") != true { f.append("FontVariantNumeric: tabular-nums") }
        if FontVariantLigaturesExtractor.extract(from: props([("FontVariantLigatures", .string("common-ligatures"))]))?.keywords.first != "common-ligatures" {
            f.append("FontVariantLigatures: common-ligatures")
        }
        if FontVariantEastAsianExtractor.extract(from: props([("FontVariantEastAsian", .string("jis78"))]))?.keywords.first != "jis78" {
            f.append("FontVariantEastAsian: jis78")
        }
        if FontVariantPositionExtractor.extract(from: props([("FontVariantPosition", .string("super"))]))?.keywords.first != "super" {
            f.append("FontVariantPosition: super")
        }
        if FontVariantAlternatesExtractor.extract(from: props([("FontVariantAlternates", .string("historical-forms"))]))?.keywords.first != "historical-forms" {
            f.append("FontVariantAlternates: historical-forms")
        }
        if FontVariantEmojiExtractor.extract(from: props([("FontVariantEmoji", .string("emoji"))]))?.keywords.first != "emoji" {
            f.append("FontVariantEmoji: emoji")
        }
        return f
    }

    // MARK: - line/

    private static func runLineChecks() -> [String] {
        var f: [String] = []
        if LineHeightExtractor.extract(from: props([("LineHeight", obj(["px": .double(24)]))]))?.px != 24 {
            f.append("LineHeight: 24pt")
        }
        if LineClampExtractor.extract(from: props([("LineClamp", .int(3))]))?.lines != 3 {
            f.append("LineClamp: 3")
        }
        if MaxLinesExtractor.extract(from: props([("MaxLines", .int(5))]))?.lines != 5 {
            f.append("MaxLines: 5")
        }
        return f
    }

    // MARK: - spacing/

    private static func runSpacingChecks() -> [String] {
        var f: [String] = []
        if LetterSpacingExtractor.extract(from: props([("LetterSpacing", obj(["px": .double(2)]))]))?.px != 2 {
            f.append("LetterSpacing: 2pt")
        }
        if WordSpacingExtractor.extract(from: props([("WordSpacing", obj(["px": .double(4)]))]))?.px != 4 {
            f.append("WordSpacing: 4pt")
        }
        if TabSizeExtractor.extract(from: props([("TabSize", .int(8))]))?.count != 8 {
            f.append("TabSize: 8")
        }
        if TextIndentExtractor.extract(from: props([("TextIndent", obj(["px": .double(16)]))]))?.px != 16 {
            f.append("TextIndent: 16pt")
        }
        return f
    }

    // MARK: - decoration/

    private static func runDecorationChecks() -> [String] {
        var f: [String] = []
        let dl = TextDecorationLineExtractor.extract(from: props([
            ("TextDecorationLine", .string("underline line-through")),
        ]))
        if dl?.underline != true || dl?.lineThrough != true {
            f.append("TextDecorationLine: underline + line-through")
        }
        if TextDecorationStyleExtractor.extract(from: props([("TextDecorationStyle", .string("dashed"))]))?.pattern != .dashed {
            f.append("TextDecorationStyle: dashed")
        }
        if TextDecorationColorExtractor.extract(from: props([("TextDecorationColor", srgb(1, 0, 0))]))?.color == nil {
            f.append("TextDecorationColor: red")
        }
        if TextDecorationThicknessExtractor.extract(from: props([("TextDecorationThickness", obj(["px": .double(2)]))]))?.px != 2 {
            f.append("TextDecorationThickness: 2pt")
        }
        if TextUnderlineOffsetExtractor.extract(from: props([("TextUnderlineOffset", obj(["px": .double(3)]))]))?.px != 3 {
            f.append("TextUnderlineOffset: 3pt")
        }
        if TextUnderlinePositionExtractor.extract(from: props([("TextUnderlinePosition", .string("under"))]))?.keyword != "under" {
            f.append("TextUnderlinePosition: under")
        }
        let ts = TextShadowExtractor.extract(from: props([("TextShadow", .array([
            obj(["x": obj(["px": .double(1)]), "y": obj(["px": .double(2)]),
                 "blur": obj(["px": .double(3)]), "c": srgb(0, 0, 0)]),
        ]))]))
        if ts?.layers.first?.radius != 3 { f.append("TextShadow: blur radius") }
        // TextTransform uppercase — nested Optional: outer .some, inner .some(.uppercase)
        let tt = TextTransformExtractor.extract(from: props([("TextTransform", .string("uppercase"))]))?.textCase
        if tt == nil || tt! != .some(Text.Case.uppercase) {
            f.append("TextTransform: uppercase")
        }
        return f
    }

    // MARK: - wrapping/

    private static func runWrappingChecks() -> [String] {
        var f: [String] = []
        if TextAlignExtractor.extract(from: props([("TextAlign", .string("CENTER"))]))?.alignment != .center {
            f.append("TextAlign: CENTER")
        }
        if TextAlignLastExtractor.extract(from: props([("TextAlignLast", .string("end"))]))?.keyword != "end" {
            f.append("TextAlignLast: end")
        }
        if TextJustifyExtractor.extract(from: props([("TextJustify", .string("auto"))]))?.keyword != "auto" {
            f.append("TextJustify: auto")
        }
        if TextWrapExtractor.extract(from: props([("TextWrap", .string("balance"))]))?.keyword != "balance" {
            f.append("TextWrap: balance")
        }
        if WhiteSpaceExtractor.extract(from: props([("WhiteSpace", .string("pre-wrap"))]))?.keyword != "pre-wrap" {
            f.append("WhiteSpace: pre-wrap")
        }
        if WordBreakExtractor.extract(from: props([("WordBreak", .string("break-all"))]))?.keyword != "break-all" {
            f.append("WordBreak: break-all")
        }
        if OverflowWrapExtractor.extract(from: props([("OverflowWrap", .string("anywhere"))]))?.keyword != "anywhere" {
            f.append("OverflowWrap: anywhere")
        }
        if LineBreakExtractor.extract(from: props([("LineBreak", .string("strict"))]))?.keyword != "strict" {
            f.append("LineBreak: strict")
        }
        if HyphensExtractor.extract(from: props([("Hyphens", .string("auto"))]))?.mode != "auto" {
            f.append("Hyphens: auto")
        }
        if HyphenateCharacterExtractor.extract(from: props([("HyphenateCharacter", .string("-"))]))?.keyword != "-" {
            f.append("HyphenateCharacter: -")
        }
        // TextOverflow ellipsis
        if case .some(.ellipsis) = TextOverflowExtractor.extract(from: props([("TextOverflow", .string("ellipsis"))]))?.mode {
            // ok
        } else { f.append("TextOverflow: ellipsis") }
        return f
    }

    // MARK: - writing/

    private static func runWritingChecks() -> [String] {
        var f: [String] = []
        if DirectionExtractor.extract(from: props([("Direction", .string("rtl"))]))?.direction != .rightToLeft {
            f.append("Direction: rtl")
        }
        if UnicodeBidiExtractor.extract(from: props([("UnicodeBidi", .string("isolate"))]))?.keyword != "isolate" {
            f.append("UnicodeBidi: isolate")
        }
        if WritingModeExtractor.extract(from: props([("WritingMode", .string("vertical-rl"))]))?.isVertical != true {
            f.append("WritingMode: vertical-rl")
        }
        if TextOrientationExtractor.extract(from: props([("TextOrientation", .string("upright"))]))?.keyword != "upright" {
            f.append("TextOrientation: upright")
        }
        if VerticalAlignExtractor.extract(from: props([("VerticalAlign", .string("super"))]))?.offsetPx != 4 {
            f.append("VerticalAlign: super")
        }
        return f
    }

    // MARK: - other/

    private static func runOtherChecks() -> [String] {
        var f: [String] = []
        if QuotesExtractor.extract(from: props([("Quotes", .string("none"))]))?.keyword != "none" {
            f.append("Quotes: none")
        }
        if TextRenderingExtractor.extract(from: props([("TextRendering", .string("optimizespeed"))]))?.keyword != "optimizespeed" {
            f.append("TextRendering: optimizespeed")
        }
        return f
    }

    // MARK: - unsupported/

    private static func runUnsupportedChecks() -> [String] {
        var f: [String] = []
        if UnsupportedSvgTypographyExtractor.extract(from: props([
            ("AlignmentBaseline", .string("middle")),
        ]))?.rawByType["AlignmentBaseline"] != "middle" {
            f.append("Unsupported SVG: AlignmentBaseline capture")
        }
        if UnsupportedPrintTypographyExtractor.extract(from: props([
            ("Orphans", .int(3)),
        ]))?.touched != true {
            f.append("Unsupported Print: Orphans touched")
        }
        if UnsupportedRubyEmphasisExtractor.extract(from: props([
            ("RubyAlign", .string("center")),
        ]))?.rawByType["RubyAlign"] != "center" {
            f.append("Unsupported Ruby/Emphasis: RubyAlign capture")
        }
        if UnsupportedFontMetaExtractor.extract(from: props([
            ("FontDisplay", .string("swap")),
        ]))?.rawByType["FontDisplay"] != "swap" {
            f.append("Unsupported FontMeta: FontDisplay capture")
        }
        if UnsupportedSpacingExtractor.extract(from: props([
            ("TextSpacing", .string("auto")),
        ]))?.rawByType["TextSpacing"] != "auto" {
            f.append("Unsupported Spacing: TextSpacing capture")
        }
        return f
    }

    // MARK: - Aggregate (end-to-end)

    private static func runAggregateChecks() -> [String] {
        var f: [String] = []
        // Empty property list → nil aggregate (short-circuit path).
        if TypographyExtractor.extract(from: []) != nil {
            f.append("Aggregate: empty list should return nil")
        }
        // Rich end-to-end: size + weight + italic + underline + line-height.
        let agg = TypographyExtractor.extract(from: props([
            ("FontSize",   obj(["px": .double(20)])),
            ("FontWeight", .string("bold")),
            ("FontStyle",  .string("italic")),
            ("TextDecorationLine", .string("underline")),
            ("LineHeight", obj(["px": .double(28)])),
        ]))
        if agg?.fontSizePx != 20        { f.append("Aggregate: fontSizePx") }
        if agg?.fontWeight != .bold     { f.append("Aggregate: fontWeight") }
        if agg?.italic != true          { f.append("Aggregate: italic") }
        if agg?.underline != true       { f.append("Aggregate: underline") }
        if agg?.lineHeightPx != 28      { f.append("Aggregate: lineHeightPx") }
        if agg?.touched != true         { f.append("Aggregate: touched flag") }
        return f
    }
}
