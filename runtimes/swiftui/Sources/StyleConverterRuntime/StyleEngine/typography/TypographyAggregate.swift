//
//  TypographyAggregate.swift
//  StyleEngine/typography — Phase 6.
//
//  Typography in SwiftUI is unusual: most of the CSS-text family ends up
//  on a single `Text` via chained modifiers (.font, .tracking, .kerning,
//  .baselineOffset, .multilineTextAlignment, .lineLimit, .truncationMode,
//  .textCase, …). If each triplet's Applier attached its own modifier,
//  the compile-time type of the result would explode and, worse, several
//  of the modifiers need to be *composed* (font-size + font-weight +
//  font-style all feed one `.font(...)` call).
//
//  Therefore every triplet's Applier contributes into a single
//  `TypographyAggregate`. `StyleBuilder.applyStyle(_:)` then hands the
//  aggregate to `TypographyApplier` once, which emits the full chain of
//  SwiftUI modifiers. This keeps per-property files small (~30-80 lines)
//  and keeps type erasure contained to the single `TypographyApplier`.
//

// SwiftUI for Font / Color / TextAlignment / LayoutDirection.
import SwiftUI

/// Rolled-up typography state produced by the Phase 6 extractors.
///
/// All fields default to `nil` (or identity) so an empty aggregate
/// produces zero SwiftUI modifiers — the applier short-circuits. Every
/// rendering-capable typography triplet's Applier has a pure
/// `contribute(into:)` reducer that sets one or two of these fields.
struct TypographyAggregate: Equatable {

    // MARK: - Font composition (FontSize + FontWeight + FontStyle + …)

    /// Resolved font size in points, post-keyword resolution. `nil` → inherit.
    var fontSizePx: CGFloat? = nil
    /// Font weight (100–900 mapped to SwiftUI `Font.Weight`). `nil` → inherit.
    var fontWeight: Font.Weight? = nil
    /// Lane IOS-TEXT fix 5 — the RAW numeric CSS weight (100…1000).
    /// Kept alongside the bucketed SwiftUI weight because CoreText's
    /// nearest-face pick on `.weight()` rounds DOWN at 600/800 with the
    /// 4-face Inter family; PlaceholderLabel instead selects the concrete
    /// installed face per css-fonts-4 §5.2 (FontFaceMatcher) using this
    /// number. `nil` → keyword-only/unknown weight, legacy path.
    var fontWeightNumeric: Int? = nil
    /// `italic | oblique` → true. `nil` → inherit.
    var italic: Bool? = nil
    /// `font-family` first concrete name, or nil for "system / inherit".
    /// Generic families (`serif`, `sans-serif`, `monospace`) resolve via the
    /// applier's keyword fallback. See FontFamilyApplier.swift.
    var fontFamilyPrimary: String? = nil
    /// Full font-family fallback chain in author-declared order. The applier
    /// walks this list and picks the first face that `UIFont(name:)` can
    /// resolve, matching how Web + Android handle the CSS fallback cascade.
    /// Previously only `fontFamilyPrimary` was kept which meant any CSS
    /// `font-family: "MissingFont", "ActualFont", sans-serif` silently
    /// rendered as the system font instead of ActualFont.
    var fontFamilyNames: [String] = []
    /// True when the family list contained a `monospace` / `ui-monospace`
    /// generic — lets the applier pick `.system(.body, design: .monospaced)`.
    var fontFamilyMonospace: Bool = false
    /// True when the family list contained `serif` / `ui-serif`.
    var fontFamilySerif: Bool = false
    /// True when the family list contained `ui-rounded`. Rare but explicit.
    var fontFamilyRounded: Bool = false
    /// `font-stretch` → SwiftUI has no direct API; we record the %-width so
    /// a future shader-based renderer can use it. Applier is a no-op today.
    var fontStretchPercent: CGFloat? = nil
    /// True when `small-caps` was specified via `font-variant-caps`.
    var smallCaps: Bool = false

    // MARK: - Letter / word / line metrics

    /// `letter-spacing` in points, fed to `.tracking(_:)`.
    var letterSpacingPx: CGFloat? = nil
    /// Lane IOS-TEXT fix 3 — unresolved em/rem `letter-spacing` (the wire
    /// ships px:0.0 with the true value nested at original.{v,u}).
    /// TypographyExtractor resolves it into letterSpacingPx once the
    /// element font size is known (em × font-size, rem × 16).
    var letterSpacingRelative: RelativeFontLength? = nil
    /// `word-spacing` in points — rendered by PlaceholderLabel as an
    /// AttributedString `.kern` on each space character (lane IOS-TEXT
    /// fix 2; css-text-3 §8.1 word-separator advance).
    var wordSpacingPx: CGFloat? = nil
    /// Lane IOS-TEXT fix 3 — unresolved em/rem `word-spacing`, resolved
    /// alongside letterSpacingRelative in TypographyExtractor.
    var wordSpacingRelative: RelativeFontLength? = nil
    /// `line-height` in points, fed to `.lineSpacing(_:)` after subtracting
    /// the font size (SwiftUI's `lineSpacing` is the extra space, not total).
    var lineHeightPx: CGFloat? = nil
    /// Unitless `line-height` multiplier. Resolved to lineHeightPx in
    /// `TypographyExtractor.finalise(_:)` once fontSizePx is known.
    var lineHeightMultiplier: CGFloat? = nil
    /// Wave 22 (lane FONT) — the IR DECLARED `line-height: normal`
    /// (css-fonts-4 §4.3, what the `font` shorthand resets to). Deliberately
    /// carries NO number: `normal` is a lookup into the rendered face's
    /// metrics, resolved by the renderer. It is the third state next to
    /// "a number was declared" (lineHeightPx/Multiplier) and "nothing was
    /// declared" (all nil) — see LineHeightNormal for why the distinction
    /// matters to the WPT ref-line-box calibration.
    var lineHeightIsNormal: Bool = false
    /// `text-indent` in points — applied as `.padding(.leading, …)` on the
    /// first line by the text renderer. Stored here for the future.
    var textIndentPx: CGFloat? = nil
    /// `tab-size` integer (character count). SwiftUI has no direct API.
    var tabSize: Int? = nil

    // MARK: - Alignment / direction

    /// Multiline alignment (`.leading | .center | .trailing`).
    var textAlign: TextAlignment? = nil
    /// `direction: ltr | rtl` → environment layoutDirection.
    var layoutDirection: LayoutDirection? = nil
    /// `vertical-align` length in points, fed to `.baselineOffset(_:)`.
    var baselineOffsetPx: CGFloat? = nil

    // MARK: - Decoration

    /// True when `text-decoration-line` contained `underline`.
    var underline: Bool = false
    /// True when `text-decoration-line` contained `line-through`.
    var strikethrough: Bool = false
    /// Lane IOS wave 5 — true when `text-decoration-line` contained
    /// `overline` (css-text-decor-3 §2.1). SwiftUI Text has no overline
    /// API, so PlaceholderLabel paints one Rectangle per rendered line
    /// at the line-box top (mirroring Compose's overlineSegments draw
    /// pass — the android↔ios pair is what SSIM compares). Previously
    /// captured-but-dropped, a native-pair divergence on the committed
    /// TextDecorationLine fixture (Overline/UnderOver/Triple).
    var overline: Bool = false
    /// Decoration line colour. Falls back to text colour when nil.
    var decorationColor: Color? = nil
    /// `text-decoration-style` keyword. SwiftUI `.underline(pattern:)`
    /// supports a subset — see TextDecorationStyleApplier.
    var decorationStyle: TextDecorationPattern = .solid
    /// `text-decoration-thickness` in points. SwiftUI has no direct API —
    /// recorded as a TODO. Set by TextDecorationThicknessApplier.
    var decorationThicknessPx: CGFloat? = nil
    /// `text-underline-offset` in points. Recorded for future use.
    var underlineOffsetPx: CGFloat? = nil
    /// `text-shadow` — the first layer only (SwiftUI doesn't stack Text
    /// shadows the way CSS does). Nil means "no shadow".
    var textShadow: TextShadowLayer? = nil
    /// Fidelity wave 2 — ALL text-shadow layers, in CSS order. Applied at
    /// the PlaceholderLabel glyph level via chained `.shadow(...)` calls
    /// (css-text-decor-3 §4: text-shadow paints behind the TEXT, not the
    /// element box — the old box-level TextShadowMod haloed the whole
    /// container, Typography_C18 red ring bug).
    var textShadowLayers: [TextShadowLayer] = []
    /// `text-transform` → SwiftUI `.textCase(_:)`.
    var textCase: Text.Case?? = nil   // nested Optional: outer nil = "inherit", inner nil = "explicitly none" (CSS `text-transform: none`).
    /// Lane IOS-TEXT fix 6 — `text-transform: capitalize`. No Text.Case
    /// member exists for it, so PlaceholderLabel titlecases each word at
    /// the string level (TextTransformApplier.capitalizeWords, the
    /// Compose-mirroring transform).
    var capitalizeWords: Bool = false

    // MARK: - Wrapping / truncation

    /// Fidelity wave 2 — `white-space: nowrap` / `text-wrap: nowrap`
    /// (css-text-4 §5.1): suppress line wrapping entirely. The
    /// PlaceholderLabel maps this to `.fixedSize(horizontal: true)` so
    /// the glyph run stays on one line and overflows the box exactly
    /// like the web reference (Typography_C20/C21 wrapped to 2 lines).
    var noWrap: Bool = false
    /// Lane IOS wave 5 — `white-space: pre | pre-wrap | break-spaces`
    /// PRESERVES space runs (css-text-3 §4.1.2: "collapsible white space
    /// is not collapsed"). The greedy pre-break in PlaceholderLabel
    /// splits on spaces and re-joins with single separators — a glyph-
    /// content REWRITE under preserved-whitespace modes — so this flag
    /// gates the pre-break OFF and restores the legacy soft-wrap path
    /// for those keywords (honest per-spec rendering; pre-breaking
    /// preserved text is future work).
    var preservesSpaces: Bool = false
    /// `line-clamp` / `max-lines` — the smaller of the two wins when both set.
    var lineLimit: Int? = nil
    /// `text-overflow: ellipsis` → `.truncationMode(.tail)`. When nil we
    /// leave the environment default (SwiftUI defaults to `.tail` anyway).
    var truncationMode: Text.TruncationMode? = nil

    // MARK: - Writing mode / orientation / quotes (mostly stubs)

    /// `writing-mode: vertical-rl | vertical-lr | sideways-*` → vertical flag.
    /// SwiftUI has no built-in vertical text; the applier logs a TODO.
    var verticalWritingMode: Bool = false

    // MARK: - Touch flag

    /// True when at least one Phase 6 extractor wrote into the aggregate.
    /// `TypographyApplier` checks this to short-circuit modifier chaining.
    var touched: Bool = false
}

/// Decoration-pattern enum — abstracted from SwiftUI's `Text.LineStyle.Pattern`
/// so the Config structs don't import SwiftUI. Maps 1:1 in
/// `TextDecorationStyleApplier`.
enum TextDecorationPattern: Equatable {
    /// CSS `solid` — the default SwiftUI pattern.
    case solid
    /// CSS `dashed` — maps to `.dash` on iOS 16+.
    case dashed
    /// CSS `dotted` — maps to `.dot` on iOS 16+.
    case dotted
    /// CSS `double` — SwiftUI has no native double; renders as `.solid` + TODO.
    case double
    /// CSS `wavy` — no SwiftUI pattern; renders as `.dot` with TODO note.
    case wavy
}

/// Single text-shadow layer. CSS allows a list — we keep only the first for
/// parity with SwiftUI's single-shadow `.shadow(...)` modifier on `Text`.
struct TextShadowLayer: Equatable {
    /// Shadow horizontal offset, points.
    var x: CGFloat
    /// Shadow vertical offset, points.
    var y: CGFloat
    /// Gaussian blur radius, points. CSS sets this as the 3rd value.
    var radius: CGFloat
    /// Shadow colour. SwiftUI `.shadow` accepts `nil` for system default.
    var color: Color?
}
