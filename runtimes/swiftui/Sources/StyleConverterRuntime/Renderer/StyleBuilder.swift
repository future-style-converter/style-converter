//
//  StyleBuilder.swift
//  StyleConverterTest
//
//  Extracts SwiftUI-native values from a bag of IRProperties.
//
//  Scope = "basic rendering": layout/sizing/spacing/colors/borders/text.
//  Roughly mirrors the subset of Android's StyleApplier that's enough to
//  render most components recognizably. Unknown / unsupported properties
//  are silently ignored so decoding never fails.
//

import SwiftUI

// MARK: - Config structs

struct LayoutConfig {
    enum DisplayType { case block, inline, flexRow, flexColumn, grid, none }
    enum Justify { case flexStart, flexEnd, center, spaceBetween, spaceAround, spaceEvenly }
    enum Align   { case stretch, flexStart, flexEnd, center, baseline }
    enum Wrap    { case noWrap, wrap, wrapReverse }

    var display: DisplayType = .block
    var justify: Justify     = .flexStart
    var align: Align         = .stretch
    var wrap: Wrap           = .noWrap
    var rowGap: CGFloat      = 0
    var columnGap: CGFloat   = 0
}

// Legacy SizeConfig removed — Phase 3 migrated sizing to the engine-side
// SizeConfig under StyleEngine/sizing. The new type carries full
// LengthValues instead of pre-resolved CGFloats so percent / em / vw can
// defer resolution to the applier's GeometryReader. ComponentStyle.size
// now refers directly to the engine struct.

// Legacy spacing config retained as an empty shim so old references compile
// while the engine-based PaddingConfig/MarginConfig take over. Migrated-out
// properties (Padding*, Margin*) are handled via PaddingApplier / MarginApplier
// attached through the new `spacing: SpacingConfig` field on ComponentStyle.
struct SpacingConfig {
    // Phase 2 extractor outputs. Nil when not present in the IR.
    var padding: PaddingConfig? = nil
    var margin: MarginConfig? = nil
    var gap: GapConfig? = nil
    var marginTrim: MarginTrimConfig? = nil
    // Threaded render context. FontSize is resolved during extraction.
    var context: SpacingContext = SpacingContext()
}

// Phase 5: the legacy BorderConfig is gone. Border sides, radius,
// outline, border-image, BoxShadow, and the keyword-only miscellanies
// are produced by the engine extractors under StyleEngine/borders/ and
// StyleEngine/effects/shadow/. ComponentStyle now carries the engine
// configs directly (see the Phase 5 block below).

struct TextConfig {
    var color: Color?            = nil
    var fontSize: CGFloat?       = nil
    var fontWeight: Font.Weight? = nil
    var fontItalic: Bool         = false
    var letterSpacing: CGFloat?  = nil
    var lineHeight: CGFloat?     = nil
    var textAlign: TextAlignment = .leading
    var underline: Bool          = false
    var strikethrough: Bool      = false
    // Generic-family signal mirrored from TypographyAggregate so the
    // PlaceholderLabel can build its own `.system(size:design:)` font.
    // PlaceholderLabel attaches `.font(...)` directly on its inner Text,
    // which wins over any container-level `.font` set later by
    // TypographyApplier (Apple docs: "The font modifier you set directly
    // on a Text view takes precedence over any font modifier you apply to
    // that view's enclosing container."). Without mirroring the design,
    // `font-family: serif` and friends silently dropped on the floor for
    // child-less components rendered as placeholders — 070_Typography_FontSerif
    // and 069_Typography_FontMono came out in default sans-serif on iOS
    // while Android + Web both rendered serif/mono correctly.
    var fontDesign: Font.Design  = .default
    // CSS `text-indent` in points. SwiftUI has no direct first-line-indent
    // API, so PlaceholderLabel applies this as a leading padding on the
    // glyph wrapper. That matches the visible result for the common case
    // where placeholder text fits on one line; multi-line wraps still
    // inherit the indent (incorrect per spec, but the fixture suite is
    // dominated by single-line placeholders so this lifts more SSIM
    // pairs than a more invasive NSAttributedString-based renderer
    // would risk regressing).
    var textIndentPx: CGFloat?    = nil
    // Fidelity wave 2 — `white-space: nowrap` / `text-wrap: nowrap`
    // (css-text-4 §5.1). PlaceholderLabel maps true to
    // `.fixedSize(horizontal: true)` so the glyph run never soft-wraps.
    var noWrap: Bool              = false
    // Fidelity wave 2 — `font-variant-caps: small-caps` bridged from the
    // typography aggregate. Must live here because PlaceholderLabel sets
    // `.font(...)` DIRECTLY on its Text (which overrides any container-
    // level font from TypographyApplier), so the caps variant has to be
    // composed into that font.
    var smallCaps: Bool           = false
    // Fidelity wave 2 — text-shadow layers applied at the GLYPH level
    // (css-text-decor-3 §4). One `.shadow(...)` per layer chains onto
    // the label's Text; the old box-level shadow haloed the container.
    var shadows: [TextShadowLayer] = []
}

struct EffectConfig {
    var opacity: CGFloat?   = nil
    var rotation: CGFloat?  = nil  // degrees
    var scale: CGFloat?     = nil
    // Phase 5: BoxShadow moved to StyleEngine/effects/shadow. The legacy
    // fields below are intentionally kept as `nil` defaults so the
    // EffectsModifier's ShadowMod short-circuits — the paint now runs
    // through BoxShadowApplier.
    var shadowColor: Color? = nil
    var shadowRadius: CGFloat? = nil
    var shadowX: CGFloat    = 0
    var shadowY: CGFloat    = 0
    var zIndex: Double?     = nil
}

/// Bundle of everything StyleBuilder extracts from a property list.
struct ComponentStyle {
    var layout: LayoutConfig    = LayoutConfig()
    var size: SizeConfig        = SizeConfig()
    var spacing: SpacingConfig  = SpacingConfig()
    var text: TextConfig        = TextConfig()
    var effect: EffectConfig    = EffectConfig()
    var backgroundColor: Color? = nil

    // Phase 5 — border family engine configs. Each is nil when the IR
    // carried no matching property, which lets every applier short-
    // circuit to identity.
    var borderSides: AllBordersConfig?    = nil
    var borderRadius: BorderRadiusConfig? = nil
    var borderImage: BorderImageConfig?   = nil
    var outline: OutlineConfig?           = nil
    var boxShadow: BoxShadowConfig?       = nil
    var borderMisc: BorderMiscConfig?     = nil

    // Phase 6 — typography aggregate. Populated once by
    // TypographyExtractor; consumed by TypographyApplier attached in
    // the applyStyle chain below.
    var typography: TypographyAggregate? = nil

    // Phase 7 step 2 — layout aggregate. Populated by LayoutExtractor
    // (flexbox sub-step owns the 11 flex properties). Consumed by
    // ComponentRenderer up front for container-kind selection; is not
    // wired through the modifier chain (SwiftUI stack constructors
    // take config, not modifiers).
    var layout7: LayoutAggregate? = nil

    // Phase 4 — colour + background + blend + isolation family outputs.
    // All optional: nil means "no matching property in IR" so the
    // corresponding applier short-circuits to identity.
    var color: ColorConfig?                           = nil
    var opacity: OpacityConfig?                       = nil
    var accentColor: AccentColorConfig?               = nil
    var caretColor: CaretColorConfig?                 = nil
    var backgroundImage: BackgroundImageConfig?       = nil
    var backgroundSize: BackgroundSizeConfig?         = nil
    var backgroundPosition: BackgroundPositionConfig? = nil
    var backgroundRepeat: BackgroundRepeatConfig?     = nil
    var backgroundClip: BackgroundClipConfig?         = nil
    var backgroundOrigin: BackgroundOriginConfig?     = nil
    var backgroundAttachment: BackgroundAttachmentConfig? = nil
    var blend: BlendModeConfig?                       = nil
    var isolation: IsolationConfig?                   = nil

    // Phase 8 — transforms + effects clip/filter/mask + visibility/overflow.
    // Each is nil when the IR carried no matching property, so the
    // corresponding applier short-circuits to identity.
    var transforms: TransformsAggregate? = nil
    var clipPath:   ClipConfig?          = nil
    var visibility: VisibilityConfig?    = nil
    var filter:     FilterConfig?        = nil
    var mask:       MaskConfig?          = nil

    // Fidelity wave 2 — CSS Motion Path (offset-path/-position/-rotate/
    // -distance). Nil when absent or the path shape isn't renderable;
    // MotionOffsetApplier is identity in that case.
    var motionOffset: MotionOffsetConfig? = nil

    // Fidelity wave 3 — multicol family (css-multicol-1). The applier
    // is still identity (SwiftUI has no column flow), but the typed
    // `count` drives the block-level full-width default for auto-width
    // multicol containers in ComponentRenderer (Columns_Decorated).
    var columns: ColumnsConfig? = nil
}

// MARK: - Builder

enum StyleBuilder {

    /// Consume a property list once, producing a populated ComponentStyle.
    static func build(from properties: [IRProperty]) -> ComponentStyle {
        var s = ComponentStyle()

        // Phase 2: extract FontSize first so SpacingContext has the right
        // pt value before padding/margin resolve em/rem. Default stays 16pt
        // per the CSS spec when no FontSize property is present.
        if let fs = properties.first(where: { $0.type == "FontSize" })
            .flatMap({ ValueExtractors.extractPx($0.data) }) {
            s.spacing.context.fontSizePx = Double(fs)
        }

        // Phase 2: extract each spacing family once via the new extractors.
        // Properties in `PropertyRegistry.migrated` are then skipped in the
        // legacy switch below, so there's no double-handling.
        s.spacing.padding    = PaddingExtractor.extract(from: properties)
        s.spacing.margin     = MarginExtractor.extract(from: properties)
        s.spacing.gap        = GapExtractor.extract(from: properties)
        s.spacing.marginTrim = MarginTrimExtractor.extract(from: properties)

        // Phase 3: sizing family. SizeExtractor.extract never returns nil —
        // it returns an empty config when no sizing props are present; the
        // applier short-circuits via `hasAny`. Every sizing prop name is in
        // PropertyRegistry.migrated so we don't double-dispatch below.
        s.size = SizeExtractor.extract(from: properties)

        // Phase 4: colour, background, blend, isolation. Each extractor
        // returns nil when its property wasn't in the IR, letting each
        // applier short-circuit cleanly. Every listed property type is in
        // PropertyRegistry.migrated so the legacy switch below skips them.
        s.color                = ColorExtractor.extract(from: properties)
        s.opacity              = OpacityExtractor.extract(from: properties)
        s.accentColor          = AccentColorExtractor.extract(from: properties)
        s.caretColor           = CaretColorExtractor.extract(from: properties)
        s.backgroundImage      = BackgroundImageExtractor.extract(from: properties)
        s.backgroundSize       = BackgroundSizeExtractor.extract(from: properties)
        s.backgroundPosition   = BackgroundPositionExtractor.extract(from: properties)
        s.backgroundRepeat     = BackgroundRepeatExtractor.extract(from: properties)
        s.backgroundClip       = BackgroundClipExtractor.extract(from: properties)
        s.backgroundOrigin     = BackgroundOriginExtractor.extract(from: properties)
        s.backgroundAttachment = BackgroundAttachmentExtractor.extract(from: properties)
        s.blend                = BlendModeExtractor.extract(from: properties)
        s.isolation            = IsolationExtractor.extract(from: properties)

        // Phase 5 — border family. Every extractor returns nil when no
        // matching property appears in the IR, so the appliers below
        // short-circuit cleanly. All owned property names live in
        // PropertyRegistry.migrated so the legacy switch skips them.
        s.borderSides  = BorderSideExtractor.extract(from: properties)
        s.borderRadius = BorderRadiusExtractor.extract(from: properties)
        s.outline      = OutlineExtractor.extract(from: properties)
        s.borderImage  = BorderImageExtractor.extract(from: properties)
        s.boxShadow    = BoxShadowExtractor.extract(from: properties)
        s.borderMisc   = BorderMiscExtractor.extract(from: properties)

        // Phase 6 — typography. TypographyExtractor calls every triplet
        // extractor, folds results into a single TypographyAggregate, and
        // returns nil when nothing touched it. Every typography property
        // name (including the "unsupported" groups) is in
        // PropertyRegistry.migrated so the legacy switch below skips them.
        s.typography = TypographyExtractor.extract(from: properties)

        // Phase 7 step 2 — layout aggregate (flexbox sub-step). The
        // 11 flex properties are listed in `LayoutFlexboxProperty.set`
        // and included in `PropertyRegistry.migrated` so the legacy
        // switch below skips them. `layout7` stays nil when no flex
        // property touched the aggregate, preserving legacy fallback
        // behaviour for grid/position/etc. until those sub-steps land.
        s.layout7 = LayoutExtractor.extract(from: properties)

        // Phase 8 — transforms (10 props), clip+visibility (10 props),
        // filter (2), mask (15). Each owns its property-type names in
        // PropertyRegistry.migrated so the legacy switch below skips them.
        s.transforms = TransformsExtractor.extract(from: properties)
        s.clipPath   = ClipExtractor.extract(from: properties)
        s.visibility = VisibilityExtractor.extract(from: properties)
        s.filter     = FilterExtractor.extract(from: properties)
        s.mask       = MaskExtractor.extract(from: properties)
        // Fidelity wave 2 — CSS Motion Path family (motion-1). Extracted
        // here so the applier can translate/rotate the finished box the
        // way the web reference offsets it (Layout_C14_OffsetPath).
        s.motionOffset = MotionOffsetExtractor.extract(from: properties)
        // Fidelity wave 3 — multicol family. Nil when no Column* property
        // is present; the typed count feeds ComponentRenderer's
        // block-full-width fold for auto-width multicol containers.
        s.columns = ColumnsExtractor.extract(from: properties)
        // CSS 2.1 §11.1.2 — the legacy `clip` property "applies to:
        // absolutely positioned elements" ONLY. On a static/relative
        // element web ignores `clip: rect(...)` entirely; iOS used to
        // apply it universally, truncating unpositioned boxes (and
        // slicing their borders off — effects/008_BoxModel lost its 4px
        // double border to the rect). Drop the legacy rect unless the
        // element is absolute/fixed positioned.
        if let lc = s.clipPath?.legacy, case .rect = lc {
            let pos = s.layout7?.position
            if pos != .absolute && pos != .fixed {
                s.clipPath?.legacy = nil
            }
        }
        // Compatibility bridge — mirror the flex aggregate into the
        // legacy `layout` config so any code paths still reading it
        // (PlaceholderLabel via style.layout.display for .none short-
        // circuit, GapApplier lookup) keep working. Only mirror fields
        // the legacy LayoutConfig actually carries.
        if let agg = s.layout7 {
            if let disp = agg.display {
                s.layout.display = legacyDisplay(disp, direction: agg.flexDirection)
            }
            if let j = agg.justifyContent { s.layout.justify = legacyJustify(j) }
            if let a = agg.alignItems      { s.layout.align = legacyAlign(a) }
            if let w = agg.flexWrap        { s.layout.wrap = legacyWrap(w) }
        }
        // Compatibility bridge for PlaceholderLabel in ComponentRenderer,
        // which still reads `style.text.fontSize / fontWeight / italic /
        // textAlign` directly. Mirror the aggregate's values so preview
        // labels keep reflecting the declared typography.
        if let agg = s.typography {
            if let px = agg.fontSizePx     { s.text.fontSize = px }
            if let w = agg.fontWeight      { s.text.fontWeight = w }
            if let it = agg.italic         { s.text.fontItalic = it }
            if let tr = agg.letterSpacingPx { s.text.letterSpacing = tr }
            if let lh = agg.lineHeightPx   { s.text.lineHeight = lh }
            if let ti = agg.textIndentPx   { s.text.textIndentPx = ti }
            if let a = agg.textAlign       { s.text.textAlign = a }
            s.text.underline = s.text.underline || agg.underline
            s.text.strikethrough = s.text.strikethrough || agg.strikethrough
            // Fidelity wave 2 bridges — PlaceholderLabel builds its own
            // Text, so glyph-level state must ride TextConfig:
            // nowrap (css-text-4 §5.1), small-caps (css-fonts-4 §6.6),
            // and per-layer text-shadow (css-text-decor-3 §4).
            s.text.noWrap = agg.noWrap
            s.text.smallCaps = agg.smallCaps
            s.text.shadows = agg.textShadowLayers
            // Generic-family bridge. Ordering mirrors FontMod.design(for:):
            // rounded > monospaced > serif > default. PlaceholderLabel uses
            // this to call `.system(size:design:)` so the design survives
            // its direct-on-Text font override.
            if agg.fontFamilyRounded {
                s.text.fontDesign = .rounded
            } else if agg.fontFamilyMonospace {
                s.text.fontDesign = .monospaced
            } else if agg.fontFamilySerif {
                s.text.fontDesign = .serif
            }
        }

        // Compatibility bridge — ComponentRenderer reads `text.color`
        // and `backgroundColor` directly (PlaceholderLabel uses the
        // latter to pick a contrasting text colour). Mirror the Phase 4
        // ColorConfig into these legacy fields. The legacy
        // BackgroundModifier was deleted so mirroring `backgroundColor`
        // no longer causes a double-paint.
        if let fg = s.color?.foreground?.toSwiftUIColor() {
            s.text.color = fg
        }
        if let bg = s.color?.background?.toSwiftUIColor() {
            s.backgroundColor = bg
        }
        // Phase 4: opacity is now painted by OpacityApplier. The legacy
        // EffectsModifier still reads `effect.opacity` — we deliberately
        // leave it nil so only one modifier applies (the new one). This
        // matches the "remove the legacy cases" instruction.

        for prop in properties {
            // Skip migrated properties — the spacing extractors above
            // have already consumed them. `contains` on a Set is O(1).
            if PropertyRegistry.migrated.contains(prop.type) { continue }

            switch prop.type {
            // ── Sizing ── migrated to StyleEngine/sizing (Phase 3). All
            // Width/Height/Min*/Max*/BlockSize/InlineSize/AspectRatio
            // flow through SizeExtractor above and are listed in
            // PropertyRegistry.migrated, so they never hit this switch.

            // ── Spacing ─── migrated to StyleEngine/spacing (Phase 2) ──

            // ── Colors ── migrated to StyleEngine/color (Phase 4) ───────
            // BackgroundColor + Color now flow through ColorExtractor /
            // ColorApplier. `text.color` is mirrored from ColorConfig
            // above so the text renderer keeps working. Both names are
            // listed in PropertyRegistry.migrated and never hit this
            // switch.

            // ── Borders ── migrated to StyleEngine/borders (Phase 5).
            // Sides, radius, outline, border-image, misc keywords, and
            // BoxShadow now flow through dedicated extractors above. Every
            // property name is in PropertyRegistry.migrated so the guard
            // at the top of the loop already skipped them.

            // ── Typography ── migrated to StyleEngine/typography (Phase 6).
            // Every font-*, line-*, text-*, white-space, word-break,
            // hyphen*, letter/word-spacing, tab-size, direction,
            // writing-mode, unicode-bidi, vertical-align, quotes,
            // text-rendering, plus the 60+ no-op grouped family props,
            // now flow through TypographyExtractor + TypographyApplier.
            // All owned names live in PropertyRegistry.migrated so the
            // guard at the top of the loop already skipped them.

            // ── Layout / display ── migrated to StyleEngine/layout
            // (Phase 7 step 2). Display, FlexDirection, FlexWrap,
            // JustifyContent, AlignItems now flow through LayoutExtractor
            // → LayoutAggregate → FlexboxApplier.containerDecision(...),
            // consumed by ComponentRenderer at container-construction time.
            // All five names plus the rest of the flex family are in
            // PropertyRegistry.migrated so they never hit this switch.
            // Gap / RowGap / ColumnGap migrated — see GapExtractor.

            // ── Effects ─────────────────────────────────────────────────
            // Opacity migrated to StyleEngine/color (Phase 4) — see
            // OpacityApplier. Not handled here.
            // Phase 8: Rotate / Scale / Translate / Transform / TransformOrigin
            // / TransformBox / TransformStyle / Perspective / PerspectiveOrigin
            // / BackfaceVisibility migrated to StyleEngine/transforms via
            // TransformsExtractor + TransformsApplier. ClipPath / ClipRule /
            // Clip migrated to StyleEngine/effects/clip. Filter / BackdropFilter
            // migrated to StyleEngine/effects/filter. Mask family migrated to
            // StyleEngine/effects/mask. Visibility / Overflow* migrated to
            // StyleEngine/visibility. All names live in PropertyRegistry
            // .migrated so the guard at the top of this loop already skipped
            // them.
            // BoxShadow migrated to StyleEngine/effects/shadow (Phase 5).
            // Handled by BoxShadowExtractor above; listed in
            // PropertyRegistry.migrated.
            // ZIndex migrated to StyleEngine/layout/position (Phase 7
            // step 4) — handled by PositionExtractor + PositionApplier.
            // Position / Top / Right / Bottom / Left / InsetBlock* /
            // InsetInline* were never in this legacy switch.

            default:
                break  // unsupported — silently skip
            }
        }

        return s
    }

    // MARK: - Parsing helpers

    // Phase 6: parseFontWeight and parseTextAlign removed — the typography
    // extractors (FontWeightExtractor, TextAlignExtractor) own these parses.

    // Phase 7 step 2: parseJustify/parseAlign replaced by
    // FlexboxExtractor.mapAlignment (engine side). The `legacy*`
    // helpers below translate the engine's AlignmentKeyword back into
    // the legacy LayoutConfig enums so ComponentRenderer's existing
    // VerticalAlignment / HorizontalAlignment bridges keep working
    // until the grid/position sub-steps land and ComponentRenderer
    // switches over to ContainerDecision wholesale.

    /// Engine DisplayKeyword → legacy LayoutConfig.DisplayType.
    /// FlexDirection is folded in here so `.flex` + column becomes
    /// `.flexColumn` — matching the original switch.
    fileprivate static func legacyDisplay(
        _ disp: DisplayKeyword,
        direction: FlexDirectionKeyword?
    ) -> LayoutConfig.DisplayType {
        switch disp {
        case .flex:
            switch direction {
            case .column, .columnReverse: return .flexColumn
            default:                      return .flexRow
            }
        case .grid:     return .grid
        case .inline:   return .inline
        case .none:     return .none
        case .contents: return .block  // best-effort approximation
        case .block:    return .block
        }
    }

    /// Engine AlignmentKeyword → legacy LayoutConfig.Justify.
    fileprivate static func legacyJustify(_ kw: AlignmentKeyword) -> LayoutConfig.Justify {
        switch kw {
        case .center:       return .center
        case .end, .selfEnd: return .flexEnd
        case .spaceBetween: return .spaceBetween
        case .spaceAround:  return .spaceAround
        case .spaceEvenly:  return .spaceEvenly
        default:            return .flexStart
        }
    }

    /// Engine AlignmentKeyword → legacy LayoutConfig.Align.
    fileprivate static func legacyAlign(_ kw: AlignmentKeyword) -> LayoutConfig.Align {
        switch kw {
        case .center:               return .center
        case .start, .selfStart:    return .flexStart
        case .end, .selfEnd:        return .flexEnd
        case .baseline:             return .baseline
        default:                    return .stretch
        }
    }

    /// Engine FlexWrapKeyword → legacy LayoutConfig.Wrap.
    fileprivate static func legacyWrap(_ kw: FlexWrapKeyword) -> LayoutConfig.Wrap {
        switch kw {
        case .wrap:         return .wrap
        case .wrapReverse:  return .wrapReverse
        case .nowrap:       return .noWrap
        }
    }

    // Phase 5: applyBoxShadow removed. BoxShadow is now handled by
    // BoxShadowExtractor + BoxShadowApplier under StyleEngine/effects/
    // shadow — this includes multi-layer composition, inset shadows,
    // and spread (all of which the legacy helper silently dropped).

    // MARK: - Fidelity wave 1 helpers

    /// CSS Backgrounds 3 §2.4 — the concrete shrink band for
    /// `background-clip`:
    ///   • border-box (default) → zero (paint under the border too)
    ///   • padding-box          → inset by each side's border width
    ///   • content-box          → inset by border width + padding
    /// Pure so BackgroundClipTests can pin the arithmetic; padding
    /// resolves through the same SpacingResolver lane the padding
    /// applier uses (percent sides fall back to the viewport basis —
    /// the same approximation PaddingApplier's fast path makes).
    static func backgroundClipInsets(_ style: ComponentStyle) -> EdgeInsets {
        // Which band? border-box / text / absent → no shrink.
        let mode = style.backgroundClip?.mode
        guard mode == .paddingBox || mode == .contentBox else { return EdgeInsets() }
        // Border band: effective width of each side that paints.
        let b = style.borderSides
        var top: CGFloat      = b?.top.hasBorder    == true ? (b?.top.effectiveWidth ?? 0)    : 0
        var leading: CGFloat  = b?.start.hasBorder  == true ? (b?.start.effectiveWidth ?? 0)  : 0
        var bottom: CGFloat   = b?.bottom.hasBorder == true ? (b?.bottom.effectiveWidth ?? 0) : 0
        var trailing: CGFloat = b?.end.hasBorder    == true ? (b?.end.effectiveWidth ?? 0)    : 0
        // content-box additionally excludes the padding band.
        if mode == .contentBox, let p = style.spacing.padding {
            // Percent padding resolves against the viewport width here —
            // matches the PaddingApplier fallback basis.
            let basis = CGFloat(style.spacing.context.viewportWidth)
            func px(_ v: LengthValue) -> CGFloat {
                switch SpacingResolver.resolve(v, ctx: style.spacing.context, isPadding: true) {
                case .px(let n):      return n
                case .percent(let f): return f * basis
                case .auto, .skip:    return 0
                }
            }
            top += px(p.top); leading += px(p.left)
            bottom += px(p.bottom); trailing += px(p.right)
        }
        return EdgeInsets(top: top, leading: leading,
                          bottom: bottom, trailing: trailing)
    }

    /// Wave 5 — resolved horizontal padding band (left + right, px) for
    /// the min-content sizing lane: SizeApplier's narrow-width proposal
    /// must clear the `.padding` inside it before reaching the text.
    /// Same resolver lane as backgroundClipInsets above.
    static func horizontalPaddingPx(_ style: ComponentStyle) -> CGFloat {
        guard let p = style.spacing.padding else { return 0 }
        // Percent padding resolves against the viewport width — the
        // PaddingApplier fallback basis (same approximation as above).
        let basis = CGFloat(style.spacing.context.viewportWidth)
        func px(_ v: LengthValue) -> CGFloat {
            switch SpacingResolver.resolve(v, ctx: style.spacing.context, isPadding: true) {
            case .px(let n):      return n
            case .percent(let f): return f * basis
            case .auto, .skip:    return 0
            }
        }
        return px(p.left) + px(p.right)
    }

    /// Web-harness min-box floor decision (see MinBoxFloor below): the
    /// floor applies per axis only when the IR declared NO width/min/max
    /// on that axis — mirrors apps/web-harness ComponentRenderer.tsx
    /// (`minWidth: styles.minWidth || (max ? '0' : (width || '50px'))`).
    /// Split out as a pure function so XCTest pins the truth table.
    static func minFloor(for size: SizeConfig) -> (width: CGFloat?, height: CGFloat?) {
        // Inline axis: any explicit width-family constraint disables it.
        let w: CGFloat? = (size.width == nil && size.minWidth == nil
                           && size.maxWidth == nil) ? 50 : nil
        // Block axis: same rule with the height family.
        let h: CGFloat? = (size.height == nil && size.minHeight == nil
                           && size.maxHeight == nil) ? 30 : nil
        return (w, h)
    }
}

/// Fidelity wave 1 — the 50×30 minimum box every OTHER platform already
/// applies (web: fit-content + minWidth 50px/minHeight 30px defaults;
/// Android: Modifier.defaultMinSize(50.dp, 30.dp)). Sits inside the
/// paint chain so background/border cover the floored area. Top-leading
/// keeps content at the block-flow origin like a browser box.
private struct MinBoxFloor: ViewModifier {
    /// The component's sizing bag — decides which axes get the floor.
    let size: SizeConfig

    func body(content: Content) -> some View {
        // Per-axis floors (nil = axis already constrained by the IR).
        let floor = StyleBuilder.minFloor(for: size)
        if floor.width == nil && floor.height == nil {
            // Fully constrained → identity, no extra frame node.
            content
        } else {
            // `.frame(minWidth:)` never expands past the child's ideal
            // size (upper bound stays the child's own width), so this is
            // a pure floor — boxes still hug content above 50×30.
            content.frame(minWidth: floor.width, minHeight: floor.height,
                          alignment: .topLeading)
        }
    }
}

// MARK: - View modifier

extension View {
    /// Apply a ComponentStyle to this view. Does everything except layout-container
    /// choice (that's ComponentRenderer's job) and spacing-outside-border cases
    /// that need the caller to know container context.
    @ViewBuilder
    func applyStyle(_ style: ComponentStyle) -> some View {
        self
            // Fidelity wave 1 — CSS box model: content sits INSIDE the
            // border band (CSS 2.1 §8.1). The border strokes paint as an
            // overlay on the border box, so without this inner inset the
            // first `border-width` points of content were painted OVER
            // (borders/001_C02, 007_C08, 008_C09 text displacement).
            // Innermost so an explicit `width` still reads border-box.
            .engineBorderContentInset(style.borderSides)
            // Phase 2: padding goes INSIDE the size frame so total width
            // reads as `width` (border-box semantics). Previously sizing
            // wrapped padding (content-box), so a fixture like Card_Complete
            // — `width: 250px; padding: 20px` — rendered as 250+40=290 on
            // iOS while web (`* { box-sizing: border-box }`) and Android
            // (Compose `Modifier.width(250).padding(20)` puts padding
            // inside) both reported 250. Forcing the SwiftUI chain to
            // padding-then-frame puts padding inside the constrained
            // width, matching the other two engines. Pure-padding cases
            // (no width) are unaffected — `.padding(20)` on naked content
            // produces `content+40` either way; the optional sizing frame
            // is identity when `c.width` is nil.
            .engineSpacingPadding(style.spacing.padding, context: style.spacing.context)
            // Phase 3 — sizing applied via SizeApplier. Uses the threaded
            // SpacingContext so em/rem/vw resolve against the same 390×844
            // canvas as padding/margin. Wave 5: the padding band rides
            // along for the min-content proposal (see SizeApplier doc).
            .engineSizing(style.size, context: style.spacing.context,
                          horizontalPadding: StyleBuilder.horizontalPaddingPx(style))
            // Fidelity wave 1 — web-harness minimum-box parity. The web
            // renderer floors every component at minWidth 50 / minHeight
            // 30 unless the IR declares width/min/max for that axis, and
            // Android mirrors it via Modifier.defaultMinSize(50.dp,30.dp).
            // iOS had NO floor, so short-named children (grid-2col
            // 001_a…019_d crops) hugged their glyphs ~17pt wide while
            // web/Android boxes were 50pt. Applied INSIDE the paint chain
            // so backgrounds/borders cover the floored area; top-leading
            // matches the block-flow origin on the other platforms.
            .modifier(MinBoxFloor(size: style.size))
            // Phase 4 — painting chain. Order (from innermost outward):
            //   1. BackgroundImage: gradients sit behind solid colour so
            //      a BackgroundColor with translucency can tint them.
            //   2. BackgroundColor: solid paint, rounded-corner aware.
            //   3. BackgroundClip / Origin / Repeat / Attachment / Size:
            //      currently stubs (see per-file headers) — kept in the
            //      chain for future non-identity implementations.
            //   4. BackgroundPosition: stub today.
            // CSS `background-clip: text` masks the bg-image to the
            // glyph shape. The PlaceholderLabel paints the gradient via
            // `.foregroundStyle(LinearGradient)` in that case, so we
            // pass `nil` to engineBackgroundImage to suppress the
            // rectangular paint that would otherwise sit behind the
            // text and double-render the gradient.
            // Fidelity wave 1: the paint-area shrink for background-clip
            // padding-box/content-box (CSS Backgrounds 3 §2.4) plus the
            // viewport anchoring for background-attachment: fixed (§2.6)
            // both thread INTO the paint calls — clipping the whole view
            // after the fact would slice content/borders off.
            // Wave 8 (#36): size/position/repeat thread INTO the paint
            // call for url() raster layers (cover/contain/px sizing,
            // keyword/percent/px anchoring, per-axis tiling). Gradient
            // layers ignore them — their engineBackground* stubs below
            // stay identity, so every gradient baseline is untouched.
            .engineBackgroundImage(
                style.backgroundClip?.mode == .text ? nil : style.backgroundImage,
                clipInsets: StyleBuilder.backgroundClipInsets(style),
                attachment: style.backgroundAttachment,
                size: style.backgroundSize,
                position: style.backgroundPosition,
                repeatCfg: style.backgroundRepeat
            )
            // Wave 5: `background-clip: text` clips the SOLID background
            // to the glyph shape too (css-backgrounds-4 §2.2), not just
            // gradients — the browser paints NO rectangular box, only
            // bg-coloured text. iOS painted the full solid rect
            // (PW_Background_Effects_01, i-w 0.774). The label paints
            // the glyph tint via PlaceholderLabel's clip-text path, so
            // the rectangular paint is suppressed the same way the
            // gradient layer is above.
            .engineBackgroundColor(
                style.backgroundClip?.mode == .text ? nil : style.color,
                radius: style.borderRadius,
                clipInsets: StyleBuilder.backgroundClipInsets(style))
            .engineBackgroundClip(style.backgroundClip)
            .engineBackgroundOrigin(style.backgroundOrigin)
            .engineBackgroundRepeat(style.backgroundRepeat)
            .engineBackgroundAttachment(style.backgroundAttachment)
            .engineBackgroundSize(style.backgroundSize)
            .engineBackgroundPosition(style.backgroundPosition)
            // Phase 5 — border family. Order: image (bottom) → radius
            // clip → sides stroke → outline (outside box) → shadow
            // (stacked outside). BoxShadow comes last so `.shadow(...)`
            // stacks on the fully-painted element.
            .engineBorderImage(style.borderImage)
            .engineBorderRadius(style.borderRadius)
            // currentColor (CSS Backgrounds 3 §3.2): a border side with a
            // style but no colour inherits the element's own `color` —
            // threaded here so dotted/solid colourless sides stop
            // defaulting to black (borders/003_C04).
            .engineBorderSides(style.borderSides, radius: style.borderRadius,
                               currentColor: style.text.color)
            // Wave 5: outline-color initial = currentColor (css-ui-4
            // §4.3) — same threading as border sides above.
            .engineOutline(style.outline, radius: style.borderRadius,
                           currentColor: style.text.color)
            .engineBorderMisc(style.borderMisc)
            .engineBoxShadow(style.boxShadow, radius: style.borderRadius)
            // Phase 4 — blend / isolation / opacity. `.blendMode`
            // applies to the whole element (including already-painted
            // backgrounds) so it must come after the paint chain.
            // `.compositingGroup` and `.opacity` follow so blending
            // composites into the isolated buffer before fading.
            .engineBlendMode(style.blend)
            .engineIsolation(style.isolation)
            .engineOpacity(style.opacity)
            // Phase 4 — accent/caret tint. Accent tints descendant
            // controls; caret is a stub. Both happily go anywhere.
            .engineAccentColor(style.accentColor)
            .engineCaretColor(style.caretColor)
            // Phase 6 — typography aggregate. Collapses font-size/weight
            // /style/family + tracking + line-spacing + alignment +
            // line-limit + truncation + text-case + underline + shadow
            // into a single modifier. Attached late so it wraps over the
            // paint chain.
            .engineTypography(style.typography)
            // Phase 8 — effects chain. Order matters:
            //   1. mask clips alpha before paint
            //   2. filter applies per-pixel effects to painted pixels
            //   3. clip-path carves the final geometry
            //   4. transforms warp the finished view (rotate/scale/translate)
            //   5. visibility/overflow gates what escapes the frame.
            .engineMask(style.mask)
            .engineFilter(style.filter)
            .engineClipPath(style.clipPath)
            .engineTransforms(style.transforms)
            // Fidelity wave 2 — CSS Motion Path (motion-1 §4). Composes
            // after the transform family, mirroring the css-transforms-2
            // matrix order (transform → offset in the accumulated
            // matrix; the wave fixtures never combine both, so the
            // relative order is currently unobservable).
            .engineMotionOffset(style.motionOffset, size: style.size,
                                context: style.spacing.context)
            .engineVisibility(style.visibility)
            .modifier(EffectsModifier(effect: style.effect))
            .engineSpacingMargin(style.spacing.margin, context: style.spacing.context)
            .engineSpacingMarginTrim(style.spacing.marginTrim)
    }
}

// Phase 3: SizingModifier / ExactSizeModifier / AspectRatioModifier
// deleted — the engine-side SizeApplier replaces all three, and the
// wiring lives in `engineSizing(_:context:)` on View above.

// Phase 4: BackgroundModifier removed — BackgroundColor now paints via
// the new ColorApplier (StyleEngine/color/ColorApplier.swift), which
// replicates the rounded-corner-aware paint so the switch is pixel
// equivalent. The `backgroundColor: Color?` field on ComponentStyle is
// no longer wired to a modifier; it's kept as a compatibility mirror
// with .text.color for consumers that read it directly.

// Phase 5: BorderModifier removed. Border sides, radius, outline,
// border-image, BoxShadow, and the keyword-only miscellanies now paint
// through StyleEngine/borders and StyleEngine/effects/shadow — wired
// via the `.engineBorder*` chain in `applyStyle` above.

private struct EffectsModifier: ViewModifier {
    let effect: EffectConfig
    func body(content: Content) -> some View {
        content
            .modifier(OpacityMod(value: effect.opacity))
            // Phase 8: RotationMod / ScaleMod deleted. The transforms
            // family now flows through TransformsApplier attached via
            // `.engineTransforms` in applyStyle above.
            // ShadowMod no longer fires — BoxShadow is the new engine path.
            .modifier(ZIndexMod(value: effect.zIndex))
    }
}

private struct OpacityMod: ViewModifier {
    let value: CGFloat?
    func body(content: Content) -> some View {
        if let v = value { content.opacity(v) } else { content }
    }
}

// Phase 8: RotationMod + ScaleMod removed. TransformsApplier owns all
// rotate/scale/translate/skew/matrix/perspective composition.

// Phase 5: ShadowMod removed — BoxShadowApplier owns the paint now.

private struct ZIndexMod: ViewModifier {
    let value: Double?
    func body(content: Content) -> some View {
        if let z = value { content.zIndex(z) } else { content }
    }
}

// Phase 5: the shared `roundedShape(_:)` helper was replaced by
// `BorderRadiusShape` under StyleEngine/borders/radius — it honours
// per-corner elliptical radii, which the old RoundedRectangle helper
// couldn't express.
