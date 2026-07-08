//
//  TypographySelfTest.swift
//  StyleEngine/typography — Phase 6.
//
//  Launch-time asserts for every rendering-capable triplet under
//  StyleEngine/typography/{font,font-variant,line,spacing,decoration,
//  wrapping,writing,other} plus a handful of spot-checks on the five
//  grouped "unsupported" extractors. Same PASS/FAIL pattern as the
//  prior SelfTest modules.
//

import Foundation
import SwiftUI

enum TypographySelfTest {

    static func run() {
        var f: [String] = []
        f += runFontChecks()
        f += runFontVariantChecks()
        f += runLineChecks()
        f += runSpacingChecks()
        f += runDecorationChecks()
        f += runWrappingChecks()
        f += runWritingChecks()
        f += runOtherChecks()
        f += runUnsupportedChecks()
        f += runAggregateChecks()

        if f.isEmpty {
            print("[TypographySelfTest] PASS — typography engine green")
        } else {
            print("[TypographySelfTest] FAIL — \(f.count) check(s) failed:")
            f.forEach { print("  - \($0)") }
        }
    }

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
