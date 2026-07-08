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
            .modifier(TrackingMod(px: agg.letterSpacingPx))
            .modifier(BaselineMod(px: agg.baselineOffsetPx))
            .modifier(LineSpacingMod(agg: agg))
            .modifier(MultilineAlignMod(alignment: agg.textAlign))
            .modifier(LineLimitMod(limit: agg.lineLimit))
            .modifier(TruncationMod(mode: agg.truncationMode))
            .modifier(TextCaseMod(tc: agg.textCase))
            .modifier(UnderlineMod(agg: agg))
            .modifier(StrikethroughMod(agg: agg))
            .modifier(TextShadowMod(layer: agg.textShadow))
            .modifier(LayoutDirectionMod(direction: agg.layoutDirection))
        // Note: no WritingModeMod. A `.rotationEffect(.degrees(90))`
        // approximation was tried in Phase 12 and regressed typography SSIM
        // (see WritingModeApplier.swift header). Vertical writing stays a
        // deferred no-op until a Core-Text-based implementation lands.
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
        let resolvedName: String? = agg.fontFamilyNames
            .first(where: { UIFont(name: $0, size: 12) != nil })
            ?? agg.fontFamilyPrimary
        if let name = resolvedName {
            // Use `.custom(_:size:)` when we have an explicit face.
            font = .custom(name, size: agg.fontSizePx ?? 17)
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
        // font size when we know it; default to 17 otherwise.
        guard let lineBox = agg.lineHeightPx else { return AnyView(content) }
        let base = agg.fontSizePx ?? 17
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

private struct TextShadowMod: ViewModifier {
    let layer: TextShadowLayer?
    func body(content: Content) -> some View {
        // KNOWN BUG (TIER1 blocked-platform — typography/TextShadow):
        // SwiftUI's `.shadow()` shadows the entire content view including
        // the parent box's background, producing a halo around the whole
        // component instead of just the text glyphs. Web + Android render
        // text-shadow correctly (only on glyphs); iOS over-shadows.
        //
        // Real fix requires either:
        //   1. Restructuring this engine so Text nodes are addressable
        //      separately from their container boxes (so we can apply
        //      .shadow() at the Text level, not at the box level), OR
        //   2. Using TextRenderer/TextLayout (iOS 18+) to render glyphs
        //      with shadow at the rasterizer level.
        //
        // Until then we apply the modifier and accept the cross-platform
        // divergence honestly (was no-op'd for parity but Auditor round
        // 9 confirmed Android renders correctly via Compose TextStyle.shadow,
        // so iOS no-op was based on a false premise — reverted).
        if let l = layer {
            return AnyView(content.shadow(color: l.color ?? .black.opacity(0.5), radius: l.radius, x: l.x, y: l.y))
        }
        return AnyView(content)
    }
}

private struct LayoutDirectionMod: ViewModifier {
    let direction: LayoutDirection?
    func body(content: Content) -> some View {
        if let d = direction { content.environment(\.layoutDirection, d) } else { content }
    }
}
