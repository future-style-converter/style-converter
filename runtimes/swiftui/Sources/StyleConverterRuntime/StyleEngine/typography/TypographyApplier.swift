//
//  TypographyApplier.swift
//  StyleEngine/typography — Phase 6.
//
//  Consumes a single TypographyAggregate and emits the full SwiftUI
//  modifier chain in one pass. Kept separate from the per-property
//  Appliers so font-size + weight + style + family can be fused into a
//  single `.font(...)` (the type signature of every modifier chain is
//  compile-time distinct in SwiftUI, so we can't split this up cleanly
//  across 35 files).
//

import SwiftUI
// UIFont is provided by UIKit. SwiftUI already transitively imports it on
// iOS but we're explicit so the FontFamily fallback probe (below) compiles
// on all SDK slices.
import UIKit

struct TypographyApplier: ViewModifier {
    let aggregate: TypographyAggregate?

    /// The size FontMod / LineSpacingMod compute with when no `font-size`
    /// reached a FontFamily-only aggregate (retro R5, audit A7#5). CSS
    /// `font-size`'s initial value is `medium` (css-fonts-4 §2.5), which
    /// the UA stylesheet resolves to 16px (Blink's kDefaultFontSize) — the
    /// same 16 every other site in both runtimes bottoms out at
    /// (ComponentRenderer's `fontSize ?? 16`, UAElementFontRule,
    /// TypographyExtractor; Compose's DynamicValueResolver
    /// .DEFAULT_FONT_SIZE_PX). The old fallback here was UIKit's 17pt body
    /// size, which nothing else in either runtime uses. Liveness, honestly:
    /// on the renderer's text path ComponentRenderer puts `.font(labelFont)`
    /// (its own `fontSize ?? 16`) directly on the Text, and SwiftUI's
    /// nearest-modifier rule makes that win over this container-level
    /// font — so this fallback is the live size only where no nearer
    /// `.font` is set (environment-inheriting children, LineSpacingMod's
    /// subtraction). The fix makes the two fallbacks agree; it is not
    /// expected to move a corpus pixel (30 FontFamily-only carriers, 25 of
    /// them already sized by MonospaceUAFontSize).
    static let fallbackFontSizePx: CGFloat = 16

    /// The size FontMod / LineSpacingMod build on: the extracted
    /// `fontSizePx`, else the shared CSS-medium fallback above. Internal so
    /// TypographyFontSizeFallbackTests can pin a FontFamily-only corpus
    /// aggregate to the same 16 the Compose twin renders.
    static func resolvedFontSizePx(_ agg: TypographyAggregate) -> CGFloat {
        agg.fontSizePx ?? fallbackFontSizePx
    }

    func body(content: Content) -> some View {
        // Fast path: if no typography extractor touched the aggregate,
        // pass the content through untouched so we don't force
        // environment reads / modifier rebuilds on non-text elements.
        guard let agg = aggregate, agg.touched else {
            return AnyView(content)
        }
        return AnyView(apply(to: content, agg: agg))
    }

    // Build the modifier chain. Each modifier is conditionally attached
    // so nil fields preserve the environment value. The order follows
    // the CSS paint model: font first, then tracking/spacing, then
    // block-level alignment + line limits, then shadow/decoration.
    @ViewBuilder
    private func apply(to content: Content, agg: TypographyAggregate) -> some View {
        content
            .modifier(FontMod(agg: agg))
            // Lane IOS wave 5 (finding 2) — when word-spacing is ALSO in
            // effect, letter-spacing must NOT ride the box-level
            // `.tracking()`: SwiftUI documents tracking as OVERRIDING
            // kerning on the same Text, so the label's per-space `.kern`
            // (the word-spacing render vehicle) was silently suppressed
            // and word-spacing became a no-op while the greedy measurer
            // still added both — measure and render disagreed. In that
            // combination PlaceholderLabel bakes letter-spacing as
            // `.kern` on EVERY character instead (spaces get
            // letter+word, css-text-3 §8.1/§8.2 both add to the
            // advance), the exact model the measurer already uses — so
            // the box tracking is skipped to let the kern attributes
            // paint. Letter-spacing alone keeps the legacy tracking.
            .modifier(TrackingMod(px: agg.wordSpacingPx != nil
                                      ? nil : agg.letterSpacingPx))
            .modifier(BaselineMod(px: agg.baselineOffsetPx))
            .modifier(LineSpacingMod(agg: agg))
            .modifier(MultilineAlignMod(alignment: agg.textAlign))
            .modifier(LineLimitMod(limit: agg.lineLimit))
            .modifier(TruncationMod(mode: agg.truncationMode))
            .modifier(TextCaseMod(tc: agg.textCase))
            .modifier(UnderlineMod(agg: agg))
            .modifier(StrikethroughMod(agg: agg))
            // Fidelity wave 2: TextShadowMod removed from the box chain.
            // css-text-decor-3 §4 — text-shadow paints behind the TEXT
            // only; the box-level `.shadow()` here haloed the whole
            // painted container (Typography_C18 red ring). The glyph-
            // level shadow now applies inside PlaceholderLabel via the
            // TextConfig.shadows bridge.
            .modifier(LayoutDirectionMod(direction: agg.layoutDirection))
        // Note: no WritingModeMod HERE. Vertical writing is not handled in
        // the box-modifier chain at all: the UPRIGHT case (writing-mode:
        // vertical-rl/lr with text-orientation mixed|upright — the
        // `writing-mode` + `text-orientation` pair of css-writing-modes-4)
        // flows through Renderer/VerticalTextFlowLayout.swift —
        // ComponentRenderer gates on `VerticalUprightGate.stack` and lays
        // the run out with `VerticalUprightTextFlow` (wave 36; wave 47's Z2
        // added the vertical multicol twin). What remains unimplemented is
        // the ROTATED sideways glyph run; WritingModeApplier.swift's header
        // records why the Phase-12 `.rotationEffect(.degrees(90))`
        // approximation was rejected (it rotates glyphs but turns the
        // reported box tall→wide, so the container diverges from the web
        // reference MORE than identity does). Retro P2b (A4#4): this note
        // used to call vertical writing "a deferred no-op", which stopped
        // being true at wave 36 — wave49-final css-writing-modes/
        // available-size-011 renders upright on iOS at 0.9427 (sub-
        // threshold, not absent).
    }
}

// MARK: - Public call surface

extension View {
    /// StyleBuilder call site — mirrors the `.engineSizing` / `.engineOpacity`
    /// naming convention established by Phases 2-5.
    func engineTypography(_ aggregate: TypographyAggregate?) -> some View {
        modifier(TypographyApplier(aggregate: aggregate))
    }
}

// MARK: - Fused font modifier

// Collects size/weight/italic/family into one `.font(...)` call. Picking
// a font design based on the declared generic (monospace/serif/rounded)
// matches the Web fallback path.
private struct FontMod: ViewModifier {
    let agg: TypographyAggregate
    func body(content: Content) -> some View {
        // Pick the base font first: custom face by name, else system
        // font with the selected design.
        var font: Font?
        // Walk the CSS fallback chain in order. The first name that
        // resolves to an installed UIFont wins; if nothing resolves we fall
        // through to the system font path below. Previously only
        // `fontFamilyPrimary` was checked, which meant
        // `font-family: "MissingFont", "Arial", sans-serif` silently
        // rendered in system font instead of Arial. We still try the full
        // CSS way of calling `.custom(_:)` — SwiftUI silently falls back
        // to system on miss, so the UIFont probe here only narrows the
        // lookup; it doesn't reject already-installed faces.
        // wave-35 lane B2 — the DOCUMENT font database is consulted FIRST, and
        // per NAME rather than after the whole walk. css-fonts-4 §5 resolves a
        // family against the document's own @font-face database before any
        // system face, so `font-family: test, Arial` must take the declared
        // `test` file even though "Arial" is installed and "test" is not.
        // DocumentFontRegistry maps the CSS name to the PostScript name
        // CoreText reported for the registered file — `UIFont(name: "test")`
        // would still miss, which is why the mapping exists at all. The
        // registry is empty for every document that declared no face, so this
        // is a dictionary miss on an empty dictionary and every pre-wave-35
        // capture keeps its exact resolution.
        let registry = DocumentFontRegistry.shared
        let resolvedName: String? = agg.fontFamilyNames
            .lazy
            .compactMap { name -> String? in
                if let mapped = registry.resolvedName(for: name) { return mapped }
                return UIFont(name: name, size: 12) != nil ? name : nil
            }
            .first
            ?? agg.fontFamilyPrimary
        if let name = resolvedName {
            // Use `.custom(_:size:)` when we have an explicit face. The size
            // is the extracted one or the CSS `medium` 16px fallback — see
            // TypographyApplier.fallbackFontSizePx (retro R5, A7#5: was 17).
            font = .custom(name, size: TypographyApplier.resolvedFontSizePx(agg))
        } else if let size = agg.fontSizePx {
            // System font at the explicit pt size.
            font = .system(size: size, design: design(for: agg))
        } else {
            // Leave the environment font alone when only modifiers below
            // (weight, italic, small-caps) apply.
            font = nil
        }
        // Weight / italic / small-caps compose on top of the base font.
        if var f = font {
            if let w = agg.fontWeight { f = f.weight(w) }
            if agg.italic == true    { f = f.italic() }
            if agg.smallCaps         { f = f.smallCaps() }
            return AnyView(content.font(f))
        }
        // No base font change — chain per-modifier variants so
        // environment font is preserved. Using `.fontWeight(_:)` /
        // `.italic()` on Text without a .font is valid in iOS 16+.
        var v: AnyView = AnyView(content)
        if let w = agg.fontWeight { v = AnyView(v.fontWeight(w)) }
        if agg.italic == true    { v = AnyView(v.italic()) }
        // SmallCaps without a base font requires constructing one — skip.
        return v
    }

    // Map the aggregate's generic-family flags to SwiftUI's Font.Design.
    // Order reflects CSS fallback: rounded > monospace > serif > default.
    private func design(for a: TypographyAggregate) -> Font.Design {
        if a.fontFamilyRounded   { return .rounded }
        if a.fontFamilyMonospace { return .monospaced }
        if a.fontFamilySerif     { return .serif }
        return .default
    }
}

// MARK: - Individual leaf modifiers

private struct TrackingMod: ViewModifier {
    let px: CGFloat?
    func body(content: Content) -> some View {
        if let v = px { content.tracking(v) } else { content }
    }
}

private struct BaselineMod: ViewModifier {
    let px: CGFloat?
    func body(content: Content) -> some View {
        if let v = px { content.baselineOffset(v) } else { content }
    }
}

private struct LineSpacingMod: ViewModifier {
    let agg: TypographyAggregate
    func body(content: Content) -> some View {
        // Convert CSS line-height (total line-box height) to SwiftUI's
        // `.lineSpacing` (extra space *between* lines). Subtract the
        // font size when we know it; else the CSS `medium` 16px the
        // rest of the runtime assumes (TypographyApplier.fallbackFontSizePx,
        // retro R5 A7#5 — the old 17 made the subtraction disagree with
        // FontMod's own fallback and with every other 16-based site).
        guard let lineBox = agg.lineHeightPx else { return AnyView(content) }
        let base = TypographyApplier.resolvedFontSizePx(agg)
        let extra = max(0, lineBox - base)
        return AnyView(content.lineSpacing(extra))
    }
}

private struct MultilineAlignMod: ViewModifier {
    let alignment: TextAlignment?
    func body(content: Content) -> some View {
        if let a = alignment { content.multilineTextAlignment(a) } else { content }
    }
}

private struct LineLimitMod: ViewModifier {
    let limit: Int?
    func body(content: Content) -> some View {
        if let n = limit { content.lineLimit(n) } else { content }
    }
}

private struct TruncationMod: ViewModifier {
    let mode: Text.TruncationMode?
    func body(content: Content) -> some View {
        if let m = mode { content.truncationMode(m) } else { content }
    }
}

private struct TextCaseMod: ViewModifier {
    let tc: Text.Case??    // nested optional: outer nil → identity
    func body(content: Content) -> some View {
        guard let inner = tc else { return AnyView(content) }
        // inner is Text.Case? — `nil` here means CSS `text-transform: none`.
        return AnyView(content.textCase(inner))
    }
}

private struct UnderlineMod: ViewModifier {
    let agg: TypographyAggregate
    func body(content: Content) -> some View {
        if agg.underline {
            // Map decoration-pattern to SwiftUI's line pattern.
            let pattern = patternFor(agg.decorationStyle)
            return AnyView(content.underline(true, pattern: pattern, color: agg.decorationColor))
        }
        return AnyView(content)
    }
    private func patternFor(_ p: TextDecorationPattern) -> Text.LineStyle.Pattern {
        switch p {
        case .solid, .double: return .solid   // no native double; TODO
        case .dashed:         return .dash
        case .dotted, .wavy:  return .dot     // no native wavy; approximate
        }
    }
}

private struct StrikethroughMod: ViewModifier {
    let agg: TypographyAggregate
    func body(content: Content) -> some View {
        if agg.strikethrough {
            let pattern: Text.LineStyle.Pattern = {
                switch agg.decorationStyle {
                case .solid, .double: return .solid
                case .dashed:         return .dash
                case .dotted, .wavy:  return .dot
                }
            }()
            return AnyView(content.strikethrough(true, pattern: pattern, color: agg.decorationColor))
        }
        return AnyView(content)
    }
}

// Fidelity wave 2: TextShadowMod deleted. The box-level `.shadow()`
// haloed the entire painted container (background included) instead of
// the glyphs — see css-text-decor-3 §4. Glyph-level shadows now paint
// inside PlaceholderLabel (Renderer/ComponentRenderer.swift) from the
// TextConfig.shadows bridge, chaining one `.shadow` per CSS layer.

private struct LayoutDirectionMod: ViewModifier {
    let direction: LayoutDirection?
    func body(content: Content) -> some View {
        if let d = direction { content.environment(\.layoutDirection, d) } else { content }
    }
}
