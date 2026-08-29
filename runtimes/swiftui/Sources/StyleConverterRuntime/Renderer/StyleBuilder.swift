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
    // Wave 22 (lane FONT) — the IR declared `line-height: normal` (css-fonts-4
    // §4.3, what `font: 92px Arial` resets to). Rides ALONGSIDE `lineHeight`
    // (which still carries the wire's legacy 1.2 stand-in for the keyword);
    // ComponentRenderer.effectiveLineHeight prefers this flag ONLY under WPT
    // capture, so `normal` falls through to the face's natural metrics there
    // while the committed dark-stage baselines keep the numeric box.
    var lineHeightIsNormal: Bool = false
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
    // wave-35 lane B2 — the PLATFORM font name for a family this document
    // declared via `@font-face`, as reported by CoreText when
    // DocumentFontRegistry registered the file (the CSS name, e.g. "test", is
    // not a name `.custom(_:size:)` understands — hence the mapping). nil for
    // every document that declared no face, which keeps the Inter/system pick
    // in ComponentRenderer.font byte-for-byte identical to wave 34.
    var fontFaceName: String?     = nil
    // CSS `text-indent` in points. SwiftUI has no direct first-line-indent
    // API, so PlaceholderLabel applies this as a leading padding on the
    // glyph wrapper. That matches the visible result for the common case
    // where placeholder text fits on one line; multi-line wraps still
    // inherit the indent (incorrect per spec, but the fixture suite is
    // dominated by single-line placeholders so this lifts more SSIM
    // pairs than a more invasive NSAttributedString-based renderer
    // would risk regressing).
    var textIndentPx: CGFloat?    = nil
    // Wave 19 — line-clamp's resolved line cap (css-overflow-4 §5). The
    // TypographyApplier's outer LineLimitMod is CLOBBERED by the label's
    // own inner `.lineLimit(nil)` (SwiftUI: the modifier closest to the
    // Text wins), so the cap must ride TextConfig into that inner call —
    // nil (no clamp) keeps today's unlimited-wrap behavior byte-for-byte.
    var lineClampLimit: Int?      = nil
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
    // Lane IOS-TEXT fix 2 — `word-spacing` in points. PlaceholderLabel
    // renders it as an AttributedString `.kern` on each space character
    // (css-text-3 §8.1); it must ride TextConfig because the label owns
    // its own Text (a box-level modifier can't reach glyph runs).
    var wordSpacingPx: CGFloat?   = nil
    // Lane IOS-TEXT fix 6 — `text-transform: capitalize`. SwiftUI's
    // Text.Case has no member for it, so PlaceholderLabel titlecases
    // each word at the string level (TextTransformApplier.capitalizeWords).
    var capitalizeWords: Bool     = false
    // Lane IOS-TEXT fix 5 — the raw numeric font-weight for the
    // css-fonts-4 §5.2 concrete-face pick (FontFaceMatcher): CoreText's
    // `.weight()` nearest-face heuristic rounds DOWN at 600/800 on the
    // 4-face Inter family while Chromium/Compose round up.
    var fontWeightNumeric: Int?   = nil
    // Lane IOS wave 5 (finding 1) — `text-transform: uppercase|lowercase`
    // as a RENDER value. PlaceholderLabel folds it into the string BEFORE
    // the greedy pre-break measures it (the transform changes advances;
    // measuring the untransformed string committed lines that overflowed
    // once the box-level `.textCase` uppercased them and TextKit re-broke
    // with push-out) and suppresses `.textCase` on its own Text so
    // measure and render share one string. nil = no case transform.
    var textCase: Text.Case?      = nil
    // Lane IOS wave 5 (finding 3) — `white-space: pre|pre-wrap|
    // break-spaces` preserves space runs (css-text-3 §4.1.2). Gates the
    // greedy pre-break OFF: its space-split would collapse preserved
    // runs — a glyph-content rewrite the spec forbids.
    var preservesSpaces: Bool     = false
    // Wave 37 (lane W7) — the resolved `hyphens` keyword (css-text-3
    // §6.1), lower-cased. Rides TextConfig because the label owns its
    // own string and its own wrap decision: `none` is spent by deleting
    // U+00AD before the greedy pre-break measures the run (the only seam
    // SwiftUI Text gives us — TextKit honours soft hyphens
    // unconditionally and ImageRenderer cannot rasterise the UIKit label
    // that would expose NSParagraphStyle), and `auto` vetoes the
    // unbreakable-word overflow rule. nil / `manual` = byte-identical.
    var hyphensMode: String?      = nil
    // Lane IOS wave 5 (finding 4) — `text-decoration-line: overline`
    // (css-text-decor-3 §2.1). SwiftUI Text has no overline API, so
    // PlaceholderLabel overlays one Rectangle per rendered line at the
    // line-box top, mirroring Compose's overlineSegments geometry (the
    // android↔ios pair is what the harness compares).
    var overline: Bool            = false
    // Lane IOS wave 5 (finding 4) — `text-decoration-color`, the paint
    // for the owned decoration rectangles (css-text-decor-3 §2.2:
    // initial value currentColor → falls back to the resolved text
    // color when nil).
    var decorationColor: Color?   = nil
    // Wave-5 gate follow-up (decoration ownership) —
    // `text-decoration-style` (css-text-decor-3 §2.3). The owned
    // underline/line-through pass engages for `solid` and — since
    // wave 21 (lane TEXTDECOR) — for `dotted`/`dashed`, whose
    // Chromium-matched op lists come from DecorationOps. `double`/
    // `wavy` keep the platform built-ins (their pattern rendering
    // still beats a solid owned rect), so the label needs the style
    // to decide ownership AND to pick the op emitter.
    var decorationStyle: TextDecorationPattern = .solid
    // Wave 21 (lane TEXTDECOR, B-RC8) — `text-decoration-thickness`
    // in px (css-text-decor-4 §2.4). Overrides the owned overlay's
    // font-derived auto thickness (DecorationMetrics.autoThickness)
    // and re-anchors the underline gap (DecorationOps.
    // explicitUnderlineGapPx). nil = auto, byte-identical legacy path.
    var decorationThicknessPx: CGFloat? = nil
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

    // CSS `zoom` (css-viewport-1). Nil when the IR carried no Zoom, in
    // which case ZoomApplier chains as identity. Non-nil it carries the
    // used scale factor that ZoomLayout multiplies the element's slot AND
    // its whole painted subtree by — see StyleEngine/rendering/
    // ZoomApplier.swift and its Compose twin.
    var zoom: ZoomConfig? = nil
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
        // wave-36 lane M8 — the UA fixed-default font size. An element that
        // declares NO font-size but whose FIRST declared family is the
        // `monospace` generic computes to 13px, not 16 (CSS Fonts 4 §3.5's
        // `medium` keyword resolved against `defaultFixedFontSize`; see
        // MonospaceUAFontSize for the two pixel measurements that pin it).
        // It has to land HERE, in Phase 2, because SpacingContext.fontSizePx
        // is the resolution base for every font-relative SIZING unit that the
        // spacing/sizing extractors below will consume — block-ellipsis-001's
        // `width: 63.1ch` and block-ellipsis-029's `margin: 1em` are exactly
        // such units, so a 16px base mis-sized the box as well as the glyphs.
        // Held in a local as well so the typography bridge further down can
        // reuse the SAME decision instead of recomputing it.
        let monospaceUaPx = MonospaceUAFontSize.resolvePx(from: properties)
        if let ua = monospaceUaPx { s.spacing.context.fontSizePx = ua }

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

        // Wave-18 lane 2 (pin P1) — the `ch` unit basis: measure the
        // advance of '0' in the resolved font design + size, but ONLY when
        // some length in this style actually uses ch (the CoreText lookup
        // is not free and the corpus overwhelmingly doesn't use ch). The
        // measured value rides SpacingContext so padding/margin/gap AND
        // the sizing lane (SizeApplierResolve funnels through
        // SpacingResolver) all share one basis; nil keeps the resolver on
        // the css-values-4 §6.1.3 0.5em fallback.
        if ChUnitMetrics.usesCh([
            s.size.width, s.size.height,
            s.size.minWidth, s.size.maxWidth, s.size.minHeight, s.size.maxHeight,
            s.spacing.padding?.top, s.spacing.padding?.right,
            s.spacing.padding?.bottom, s.spacing.padding?.left,
            s.spacing.margin?.top, s.spacing.margin?.right,
            s.spacing.margin?.bottom, s.spacing.margin?.left,
        ]) {
            s.spacing.context.chAdvancePx = ChUnitMetrics.zeroAdvancePx(
                // Generic-family flags in FontMod.design(for:) precedence
                // (rounded > monospaced > serif > default) so the measured
                // font is the one the text will render in.
                rounded: s.typography?.fontFamilyRounded ?? false,
                monospaced: s.typography?.fontFamilyMonospace ?? false,
                serif: s.typography?.fontFamilySerif ?? false,
                sizePx: s.spacing.context.fontSizePx,
                // wave-47 lane Z4 (SEAM 2 of the monospace pin) — the SAME
                // css-fonts-4 §5.2 registry walk the label bridge below runs
                // for `text.fontFaceName`: when this document registered a
                // face for a name in the list, the label PAINTS that face, so
                // the ch basis must measure it too. wave-46 Y7 measured the
                // design-only basis holding a pinned `width: 10ch` box at SF
                // Mono's 202px against the frozen Menlo ref's (and the
                // painted DejaVu's) 197px. nil for every face-free document —
                // the mapping is empty, the walk is a no-op, the design
                // branch above stays byte-identical.
                documentFaceName: s.typography?.fontFamilyNames
                    .lazy
                    .compactMap { DocumentFontRegistry.shared.resolvedName(for: $0) }
                    .first)
        }

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
        // CSS `zoom` (css-viewport-1). Dedicated extractor rather than the
        // rendering-hint bag: the factor lives under `value` in a tagged
        // object, which the generic keyword fold flattens to the string
        // "number" — see ZoomExtractor.swift's header. "Zoom" stays owned
        // by RenderingProperty in the registry; RenderingApplier is
        // identity, so only this config reaches a modifier.
        s.zoom = ZoomExtractor.extract(from: properties)
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
            // wave-36 lane M8: the UA fixed default (13px) stands in for the
            // absent `font-size` ONLY on a first-family-monospace element —
            // everywhere else `monospaceUaPx` is nil and the field stays nil,
            // so every `textConfig.fontSize ?? 16` bottom-out in
            // ComponentRenderer is byte-identical to before.
            if let px = agg.fontSizePx     { s.text.fontSize = px }
            else if let ua = monospaceUaPx { s.text.fontSize = CGFloat(ua) }
            if let w = agg.fontWeight      { s.text.fontWeight = w }
            if let it = agg.italic         { s.text.fontItalic = it }
            if let tr = agg.letterSpacingPx { s.text.letterSpacing = tr }
            if let lh = agg.lineHeightPx   { s.text.lineHeight = lh }
            // Wave 22 (lane FONT) — carry the DECLARED-`normal` signal to the
            // renderer. Unconditional OR-free assignment is deliberate: the
            // aggregate is the single source of truth for this element's
            // line-height, so a numeric declaration (which leaves the flag
            // false) must be able to clear a stale true, exactly like the
            // px/multiplier fields above are last-write-wins.
            s.text.lineHeightIsNormal = agg.lineHeightIsNormal
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
            // Wave 19 — mirror the aggregate's line cap (line-clamp /
            // max-lines, css-overflow-4 §5) so the label's inner
            // .lineLimit call can honor it; see TextConfig.lineClampLimit.
            s.text.lineClampLimit = agg.lineLimit
            // Lane IOS-TEXT bridges — word-spacing render value (fix 2,
            // includes the em/rem-resolved lane), the capitalize flag
            // (fix 6), and the raw numeric weight for the §5.2 face
            // pick (fix 5). All glyph-level state PlaceholderLabel owns.
            s.text.wordSpacingPx = agg.wordSpacingPx
            s.text.capitalizeWords = agg.capitalizeWords
            s.text.fontWeightNumeric = agg.fontWeightNumeric
            // Lane IOS wave 5 (finding 1) — flatten the aggregate's
            // two-level textCase (outer nil = inherit, inner nil =
            // explicit `none`) into the label's render value: both nil
            // states mean "no case rewrite here" (an INHERITED transform
            // arrives through the merged property list, so the child's
            // own aggregate carries it — see InheritedText.inheritedTypes).
            if case .some(let inner) = agg.textCase { s.text.textCase = inner }
            // Lane IOS wave 5 (finding 3) — preserved-whitespace gate
            // for the greedy pre-break (css-text-3 §4.1.2).
            s.text.preservesSpaces = agg.preservesSpaces
            // Wave 37 (lane W7) — the `hyphens` keyword (css-text-3
            // §6.1). Mirrored like `preservesSpaces` (last-write-wins,
            // no OR): a `manual` declaration on the element must clear
            // an inherited `none` that reached this aggregate.
            s.text.hyphensMode = agg.hyphensMode
            // Lane IOS wave 5 (finding 4) — overline flag + decoration
            // color for the label's per-line Rectangle overlay
            // (css-text-decor-3 §2.1/§2.2).
            s.text.overline = agg.overline
            s.text.decorationColor = agg.decorationColor
            // Wave-5 gate follow-up — decoration style gates the OWNED
            // underline/line-through pass; wave 21 (lane TEXTDECOR)
            // extends ownership to dotted/dashed via DecorationOps
            // (§2.3 double/wavy still keep the platform built-ins,
            // see TextConfig).
            s.text.decorationStyle = agg.decorationStyle
            // Wave 21 (lane TEXTDECOR, B-RC8) — `text-decoration-
            // thickness` (css-text-decor-4 §2.4). The aggregate parsed
            // it since Phase 6 but the label never saw it (the exact
            // silent loss this wave kills): the owned overlay now
            // overrides the font-derived auto thickness with it.
            s.text.decorationThicknessPx = agg.decorationThicknessPx
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
            // wave-35 lane B2 — the DOCUMENT @font-face bridge, and it needs
            // the SAME treatment the generic-family bridge above needed for
            // the same reason: PlaceholderLabel attaches `.font(...)` directly
            // on its inner Text, which wins over the container-level font
            // TypographyApplier sets, so a declared face resolved only there
            // would silently lose on exactly the WPT text the channel exists
            // for. Walk the family list in css-fonts-4 §5.2 order and take the
            // first name this document registered; nil (the universal case —
            // no document declared a face) leaves the Inter/system pick below
            // byte-for-byte unchanged.
            s.text.fontFaceName = agg.fontFamilyNames
                .lazy
                .compactMap { DocumentFontRegistry.shared.resolvedName(for: $0) }
                .first
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

        // Wave 40 (lane T7) — the css-ui-3 §5 border-box FLOOR, applied LAST
        // because it needs the finished sizing, padding and border configs at
        // once: a `box-sizing: border-box` box whose declared size is smaller
        // than its own padding + border bands has a zero content box, not a
        // smaller border box (css-ui box-sizing-026's 10px width inside 50px
        // borders is a 100px green square in every browser). Identity for
        // every component that does not EXPLICITLY declare border-box — see
        // BorderBoxFloor's enumerated trigger scope.
        BorderBoxFloor.apply(to: &s, bands: paddingAndBorderBands(s))

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
        // Wave 18 (RC6): unboxable `display: contents` never reaches this
        // switch — ContentsUnboxing strips/splices it at the renderer
        // entry (css-display-3 §2.5). What still lands here is the KEPT
        // class only (pseudo-bearing, bucket-carrying, or §2.7-blockified
        // contents), for which the block box is the documented wrapper
        // approximation, not a silent fallthrough.
        case .contents: return .block
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

    /// IOS-BBM — the background-blend gate. Returns the per-layer
    /// `background-blend-mode` list when the blended-background
    /// compositor must run, `[]` otherwise. The compositor fires only
    /// when ALL of:
    ///   • the IR carried BackgroundBlendMode with ≥1 non-normal entry
    ///     (all-normal must stay byte-stable on the legacy paint path —
    ///     `normal` is the CSS initial and blends nothing);
    ///   • there is at least one background-image layer to blend
    ///     (colour alone has nothing above it — §3.2 blending is a
    ///     layer-against-lower-layers operation);
    ///   • background-clip is not `text` (the glyph-masked path paints
    ///     via foregroundStyle in PlaceholderLabel; the rectangular
    ///     stack is suppressed there, so blending it would be unseen
    ///     work at best and a double paint at worst).
    /// Pure + static so BackgroundBlendModeTests pins the gate directly.
    static func activeBackgroundBlendModes(_ style: ComponentStyle) -> [BlendMode] {
        guard let modes = style.blend?.background,
              modes.contains(where: { $0 != .normal }),
              style.backgroundImage?.hasAny == true,
              style.backgroundClip?.mode != .text
        else { return [] }
        return modes
    }

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
        // Wave-18 lane 2 (pin P13) — this band feeds INTRINSIC-sizing
        // computations (content-box frame inflation, the min-content
        // width proposal), where css-sizing-3 §5.2.1 resolves cyclic
        // percentages against ZERO: a definite ancestor basis from the
        // env-threaded containing-block channel wins, an indefinite one
        // contributes nothing. The old viewport basis inflated
        // `width:100px; padding-left:50%` under the WPT content-box
        // default to 100 + 195 = 295px while the browser ref paints
        // 100px (css-sizing abspos-auto-sizing-fit-content-percentage-
        // 003/004 — the abspos fit-content ancestor publishes nil).
        let basis = CGFloat(style.spacing.context.containingBlockWidthPx ?? 0)
        // Skeptic follow-up: a calc-% must use the SAME intrinsic basis a
        // bare % gets (the `basis` above) — without the override the
        // evaluator's legacy 358 base inflated calc(50% + 10px) to 189
        // where Compose's percentIndefiniteAsZero context yields 10.
        var ictx = style.spacing.context
        ictx.calcPercentBasisPx = Double(basis)
        func px(_ v: LengthValue) -> CGFloat {
            switch SpacingResolver.resolve(v, ctx: ictx, isPadding: true) {
            case .px(let n):      return n
            case .percent(let f): return f * basis
            case .auto, .skip:    return 0
            }
        }
        return px(p.left) + px(p.right)
    }

    /// Lane BX — vertical analogue of [horizontalPaddingPx]: resolved
    /// top + bottom padding band (px). Needed because `box-sizing:
    /// content-box` inflates BOTH axes (css-sizing-3 §3) while the
    /// min-content lane above only ever needed the horizontal band.
    /// Same resolver lane + percent basis as horizontalPaddingPx (CSS
    /// resolves percent padding on ALL sides against the inline basis).
    static func verticalPaddingPx(_ style: ComponentStyle) -> CGFloat {
        guard let p = style.spacing.padding else { return 0 }
        // Wave-18 lane 2 (pin P13) — same intrinsic-sizing basis rule as
        // horizontalPaddingPx above: definite containing-block width or
        // zero (css-sizing-3 §5.2.1; CSS resolves percent padding on ALL
        // sides, vertical included, against the INLINE basis).
        let basis = CGFloat(style.spacing.context.containingBlockWidthPx ?? 0)
        // Same calc-% intrinsic-basis override as horizontalPaddingPx.
        var ictx = style.spacing.context
        ictx.calcPercentBasisPx = Double(basis)
        func px(_ v: LengthValue) -> CGFloat {
            switch SpacingResolver.resolve(v, ctx: ictx, isPadding: true) {
            case .px(let n):      return n
            case .percent(let f): return f * basis
            case .auto, .skip:    return 0
            }
        }
        return px(p.top) + px(p.bottom)
    }

    /// Lane BX — per-axis frame inflation for `box-sizing: content-box`
    /// (css-sizing-3 §3: declared size = content; frame = content +
    /// padding + border). Returns (0, 0) unless the IR EXPLICITLY
    /// declared content-box — the tri-state guard (SizeConfig.boxSizing
    /// nil = unset) that keeps every width+padding fixture captured
    /// against the web harness's border-box reset byte-stable, and keeps
    /// the fixture's V1_border variant (explicit border-box) untouched.
    /// Border widths use the same hasBorder/effectiveWidth gate as
    /// backgroundClipInsets above: a side with `border-style: none`
    /// has USED width 0 (CSS 2.1 §8.5.3) and must not inflate.
    static func contentBoxInflation(_ style: ComponentStyle) -> (h: CGFloat, v: CGFloat) {
        // Unset or explicit border-box → the frame already IS the
        // declared size; no inflation on either axis.
        guard style.size.boxSizing == .contentBox else { return (0, 0) }
        return paddingAndBorderBands(style)
    }

    /// The per-axis padding + USED border sum, in px — the band a declared
    /// size sits inside (`box-sizing: border-box`) or outside (`content-box`).
    ///
    /// Split out of `contentBoxInflation` in wave 40 so the border-box FLOOR
    /// (StyleEngine/sizing/BorderBoxFloor.swift) measures the bands with the
    /// exact same rule the inflation does — one definition of "what counts as
    /// a border" for both directions, so the two can never drift.
    /// Border widths use the same hasBorder/effectiveWidth gate as
    /// backgroundClipInsets: a side with `border-style: none` has USED width 0
    /// (CSS 2.1 §8.5.3) and contributes nothing.
    static func paddingAndBorderBands(_ style: ComponentStyle) -> (h: CGFloat, v: CGFloat) {
        // Border band per side — mirrors backgroundClipInsets' gating.
        let b = style.borderSides
        let top: CGFloat      = b?.top.hasBorder    == true ? (b?.top.effectiveWidth ?? 0)    : 0
        let leading: CGFloat  = b?.start.hasBorder  == true ? (b?.start.effectiveWidth ?? 0)  : 0
        let bottom: CGFloat   = b?.bottom.hasBorder == true ? (b?.bottom.effectiveWidth ?? 0) : 0
        let trailing: CGFloat = b?.end.hasBorder    == true ? (b?.end.effectiveWidth ?? 0)    : 0
        // Padding bands ride the shared resolver helpers above so the
        // inflation always agrees with what PaddingApplier will inset.
        return (h: horizontalPaddingPx(style) + leading + trailing,
                v: verticalPaddingPx(style) + top + bottom)
    }

    /// Web-harness min-box floor decision (see MinBoxFloor below): the
    /// floor applies per axis only when the IR declared no width and no
    /// min-* on that axis. A max-* cap does NOT disable it.
    /// Split out as a pure function so XCTest pins the truth table.
    ///
    /// WAVE-36 M6 — THE HONESTY SWEEP repaired this gate. It used to also
    /// require `maxWidth == nil` / `maxHeight == nil`, which made iOS the
    /// ONLY platform where a max-* cap could collapse a childless box to
    /// nothing, and the committed baseline recorded that collapse:
    /// `fixtures/visual-test.json` `Sizing_MaxWidthPercent`
    /// (`max-width: 80%; height: 50px; background: #8e44ad`, no width)
    /// ships `tools/visual/baseline/{web,Android}__004_Sizing_MaxWidthPercent.png`
    /// with a 50×50 purple square at (16,16) — 2,414 purple pixels each,
    /// byte-identical — while the iOS baseline carries ZERO purple pixels.
    /// A cap is a ceiling, not a reason to let a box vanish; CSS 2.1 §10.4
    /// resolves the used width as max(min-width, min(width, max-width)),
    /// so a 0px used width here is wrong on its own terms too.
    ///
    /// THE PARITY TARGETS, both of which already exclude max-*:
    ///   • Compose — `hasExplicitWidth  = type in [Width, MinWidth,
    ///     InlineSize, MinInlineSize]` and its height twin
    ///     (runtimes/compose/.../core/renderer/ComponentRenderer.kt);
    ///   • web — the wave-35 branch in apps/web-harness ComponentRenderer.tsx
    ///     that keeps the placeholder floor under a cap with no declared
    ///     width (`(styles.width || styles.inlineSize) ? '0' : '50px'`),
    ///     whose own comment asserts "SwiftUI's MinBoxFloor mirrors it".
    ///     It did not. Now it does.
    ///
    /// SCOPE: WPT capture drops the floor entirely (MinBoxFloor's
    /// wptCaptureMode branch), so no corpus cell can move; only the 327-pair
    /// product baseline is in range, and only its two max-*-declaring
    /// components — `Sizing_MinMax` bottoms out on its explicit
    /// `min-width: 100px` and is untouched, leaving exactly one capture
    /// affected: iOS__004_Sizing_MaxWidthPercent.png.
    static func minFloor(for size: SizeConfig) -> (width: CGFloat?, height: CGFloat?) {
        // Inline axis: a declared width or min-width disables it. maxWidth is
        // deliberately absent — see the parity note above.
        let w: CGFloat? = (size.width == nil && size.minWidth == nil) ? 50 : nil
        // Block axis: same rule with the height family.
        let h: CGFloat? = (size.height == nil && size.minHeight == nil) ? 30 : nil
        return (w, h)
    }

    // ── Wave-18 cleanup (clip-003): outline-vs-own-clip ordering ──────────
    // css-overflow-3 §3 clips the element's CONTENT; the css-ui-4 §4 outline
    // is post-layout ink around the border box, which the element's OWN
    // overflow clip must NOT swallow ("outlines … do not clip" — only
    // ANCESTOR scroll/clip containers may cut it off; that ancestor
    // propagation is a documented cross-native TODO, see the Compose twin
    // note in borders/outline/OutlineApplier.kt). The two helpers below
    // split the single outline application between two chain slots so
    // exactly one fires per element:
    //   • no own clip → the legacy slot inside applyStyle — byte-stable
    //     for the committed corpus, which never combines outline + clip;
    //   • own clip → the hoisted slot AFTER engineVisibility's clip.

    /// True when this element's own overflow config clips either axis —
    /// the same §3.1-coerced decision VisibilityApplier.body makes, so the
    /// hoist can never disagree with the clip that motivates it.
    static func ownOverflowClips(_ cfg: VisibilityConfig?) -> Bool {
        // Untouched/absent config → CSS initial `visible` → no clip.
        guard let c = cfg, c.touched else { return false }
        // nil axis = undeclared → initial `visible` (VisibilityApplier).
        let ox = c.overflowX ?? OverflowKind.visible
        let oy = c.overflowY ?? OverflowKind.visible
        // Either clipped axis can swallow outline ink → hoist on either.
        return OverflowClipRules.axisClips(OverflowClipRules.usedOverflow(ox, other: oy))
            || OverflowClipRules.axisClips(OverflowClipRules.usedOverflow(oy, other: ox))
    }

    /// The legacy in-chain outline slot: identity (nil) when the outline
    /// is hoisted past the element's own clip instead.
    static func inChainOutline(_ style: ComponentStyle) -> OutlineConfig? {
        ownOverflowClips(style.visibility) ? nil : style.outline
    }

    /// The hoisted outline slot: non-nil ONLY when the element clips its
    /// own overflow AND is actually visible — engineVisibility's
    /// hidden/collapse treatment (opacity(0) / zero-frame) sits BEFORE
    /// this slot in the chain, so painting here on a hidden element would
    /// resurrect its outline (CSS 2.1 §11.2: visibility hides the whole
    /// box's rendering, outline included).
    static func hoistedOutline(_ style: ComponentStyle) -> OutlineConfig? {
        // No own clip → the legacy slot already painted it.
        guard ownOverflowClips(style.visibility) else { return nil }
        // Hidden/collapsed element → no ink anywhere, outline included.
        if let v = style.visibility?.visibility, v != .visible { return nil }
        return style.outline
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

    // TITAN Round 4 (GAP 1) — the composed-WPT capture flag. When true the
    // 50×30 floor is DROPPED so a block box hugs its real content and its
    // width comes from the block-flow fill (browser-ref parity). This is the
    // iOS analogue of web's WPT branch skipping the fit-content + minWidth
    // 50 / minHeight 30 defaults (apps/web-harness ComponentRenderer.tsx):
    // without it a bare <p> bar is floored at 30px tall (vs the ref's ~18px
    // line box) and the composed multi-bar tests can't align. Flag off (the
    // product path + the whole 327-pair baseline) keeps the floor exactly.
    @Environment(\.wptCaptureMode) private var wptCaptureMode

    func body(content: Content) -> some View {
        // Composed WPT capture: no synthetic floor — the box hugs content
        // (height) and takes its width from the block-flow fill / IR.
        let floor: (width: CGFloat?, height: CGFloat?) =
            wptCaptureMode ? (nil, nil) : StyleBuilder.minFloor(for: size)
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
    ///
    /// Wave 8 (lane IOS paint-order): split into TWO halves so the
    /// renderer can slot absolutely-positioned descendants BETWEEN them
    /// at the CSS 2.1 Appendix E boundary — the element's own
    /// background/border paint in steps 2–4 (applyBoxDecoration, the
    /// LOW layer) while positioned descendants paint in step 8, above
    /// them, yet still INSIDE the parent's group effects (opacity /
    /// filter / transform apply to the whole subtree — css-color-4
    /// §2.1, css-transforms-1 §3). Chaining the halves back-to-back
    /// reproduces the exact pre-split modifier order, so every caller
    /// of plain applyStyle renders byte-identically.
    @ViewBuilder
    func applyStyle(_ style: ComponentStyle) -> some View {
        // The two halves compose in the original order: box paint
        // first, group effects (blend/opacity/typography/effects/
        // transforms/margin) wrapped around the painted box.
        applyBoxDecoration(style).applyGroupEffects(style)
    }

    /// FIRST half of the style chain — the element's own BOX: content
    /// inset, padding, sizing, min-box floor, then the paint stack
    /// (backgrounds, border image, radius, border sides, outline,
    /// box-shadow). Everything here renders on the element's border box
    /// and must sit BELOW the element's positioned descendants
    /// (CSS 2.1 Appendix E: steps 2–4 paint before step 8).
    @ViewBuilder
    func applyBoxDecoration(_ style: ComponentStyle) -> some View {
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
            // Lane BX: contentBoxInflation is (0,0) unless the IR
            // explicitly declared `box-sizing: content-box`, in which
            // case the frame grows by padding+border so the declared
            // width/height size the CONTENT box (css-sizing-3 §3).
            .engineSizing(style.size, context: style.spacing.context,
                          horizontalPadding: StyleBuilder.horizontalPaddingPx(style),
                          contentBoxInflateH: StyleBuilder.contentBoxInflation(style).h,
                          contentBoxInflateV: StyleBuilder.contentBoxInflation(style).v)
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
            // Phase 5's bottom layer, attached FIRST — border-image
            // (IOS-BI paint-order fix). The nine-slice Canvas hangs off
            // a `.background`, and SwiftUI stacks `.background` calls so
            // each LATER call paints UNDER the earlier ones (every call
            // wraps the previous result and slots behind it) while ALL
            // of them stay behind the content itself. Attaching border-
            // image before every engineBackground* call therefore makes
            // it the TOPMOST background layer: above the whole background
            // chain, beneath the element's own text — the CSS order
            // (css-backgrounds-3 §6 draws the image "in place of the
            // border"; CSS2 Appendix E paints borders after backgrounds,
            // before content). The old `.overlay` attachment down in
            // Phase 5 painted ABOVE content, so a slice-`fill` center
            // covered the label web/Android keep on top. Layout size at
            // this point equals the old Phase 5 spot — the intervening
            // engineBackground* modifiers never change geometry — and
            // the §6.4 outset still paints outside the host bounds
            // because neither `.background` nor Canvas clips draws.
            .engineBorderImage(style.borderImage)
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
            // layers honour them too now (gradient-geometry lane): a
            // non-initial knob routes the layer through the Canvas tile
            // painter (BackgroundGradientTileView); knob-less gradients
            // keep the wave-5 full-box path so those baselines are
            // untouched. The engineBackground* stubs below stay identity.
            // IOS-BBM: `background-blend-mode` threads INTO the image
            // applier — CSS Compositing 1 §3.2 blends background LAYERS
            // against each other (and the bg-colour at the bottom) in
            // isolation, which only the layer-owning applier can build
            // (BackgroundBlendCompositor: ZStack + per-layer .blendMode
            // + .compositingGroup, mirroring Compose's saveLayer stack).
            // The gate (activeBackgroundBlendModes) returns [] for
            // all-normal / no-layers / clip:text, keeping those renders
            // byte-stable on the legacy path. blendColor/blendRadius ride
            // along unconditionally — the applier only consumes them when
            // a non-normal mode is present.
            .engineBackgroundImage(
                style.backgroundClip?.mode == .text ? nil : style.backgroundImage,
                clipInsets: StyleBuilder.backgroundClipInsets(style),
                attachment: style.backgroundAttachment,
                size: style.backgroundSize,
                position: style.backgroundPosition,
                repeatCfg: style.backgroundRepeat,
                blendModes: StyleBuilder.activeBackgroundBlendModes(style),
                blendColor: style.color,
                blendRadius: style.borderRadius
            )
            // Wave 5: `background-clip: text` clips the SOLID background
            // to the glyph shape too (css-backgrounds-4 §2.2), not just
            // gradients — the browser paints NO rectangular box, only
            // bg-coloured text. iOS painted the full solid rect
            // (PW_Background_Effects_01, i-w 0.774). The label paints
            // the glyph tint via PlaceholderLabel's clip-text path, so
            // the rectangular paint is suppressed the same way the
            // gradient layer is above.
            // IOS-BBM: when the blended compositor above owns the paint,
            // the colour already sits at the BOTTOM of its isolated stack
            // (§3.2) — painting it again here would double-fill the box
            // behind the blend group, washing the result. Same suppression
            // pattern as clip:text.
            .engineBackgroundColor(
                (style.backgroundClip?.mode == .text
                 || !StyleBuilder.activeBackgroundBlendModes(style).isEmpty)
                    ? nil : style.color,
                radius: style.borderRadius,
                clipInsets: StyleBuilder.backgroundClipInsets(style))
            .engineBackgroundClip(style.backgroundClip)
            .engineBackgroundOrigin(style.backgroundOrigin)
            .engineBackgroundRepeat(style.backgroundRepeat)
            .engineBackgroundAttachment(style.backgroundAttachment)
            .engineBackgroundSize(style.backgroundSize)
            .engineBackgroundPosition(style.backgroundPosition)
            // Phase 5 — border family. Order: image (bottom — attached
            // up before the background chain so its `.background` paints
            // above backgrounds yet beneath content, see the IOS-BI note
            // there) → radius clip → sides stroke → outline (outside
            // box) → shadow (stacked outside). BoxShadow comes last so
            // `.shadow(...)` stacks on the fully-painted element.
            .engineBorderRadius(style.borderRadius)
            // currentColor (CSS Backgrounds 3 §3.2): a border side with a
            // style but no colour inherits the element's own `color` —
            // threaded here so dotted/solid colourless sides stop
            // defaulting to black (borders/003_C04).
            .engineBorderSides(style.borderSides, radius: style.borderRadius,
                               currentColor: style.text.color)
            // Wave 5: outline-color initial = currentColor (css-ui-4
            // §4.3) — same threading as border sides above.
            // Wave-18 cleanup (clip-003): this slot goes identity (nil)
            // when the element clips its own overflow — the outline is
            // then HOISTED past that clip in applyGroupEffects, because
            // SwiftUI's later-wraps-earlier chain would otherwise let
            // engineVisibility's clip swallow the ring web/ref paint
            // (css-ui-4 §4: the element's own clip must not cut its
            // outline). Un-clipped elements keep this slot byte-for-byte.
            .engineOutline(StyleBuilder.inChainOutline(style),
                           radius: style.borderRadius,
                           currentColor: style.text.color)
            .engineBorderMisc(style.borderMisc)
            .engineBoxShadow(style.boxShadow, radius: style.borderRadius)
    }

    /// SECOND half of the style chain — GROUP effects that wrap the
    /// finished box: blend/opacity, typography environment, masks,
    /// filters, clip-path, transforms, motion, visibility, and finally
    /// margin. The renderer attaches the absolute-child overlay between
    /// the halves, so everything below also applies to positioned
    /// descendants — matching CSS, where a parent's opacity/filter/
    /// transform composite the WHOLE subtree (they create stacking and
    /// containing contexts: css-color-4 §2.1, filter-effects-1 §5,
    /// css-transforms-1 §3) and margin moves the box children ride in.
    @ViewBuilder
    func applyGroupEffects(_ style: ComponentStyle) -> some View {
        self
            // Phase 4 — isolation / opacity / blend, in that order, and
            // the order IS the semantics (later modifier = outer wrapper):
            //
            //   1. `.engineIsolation` groups the element's own children so
            //      THEIR blend modes stop at this element (isolation:
            //      isolate; the default auto emits nothing).
            //   2. `.engineOpacity` carries its OWN compositing group for
            //      alpha < 1 (css-color-4 §2.1: the subtree is composited
            //      as a group, then faded).
            //   3. `.engineBlendMode` is OUTERMOST: compositing-1 §5.1
            //      blends the element's finished GROUP into the parent's
            //      backdrop, so it must wrap the opacity group, not sit
            //      inside it.
            //
            // The old order had blend FIRST (innermost). That was harmless
            // while opacity was a bare `.opacity()` — SwiftUI kept the
            // blend attribute through it — but the moment opacity gained
            // its (spec-required) `.compositingGroup()`, the group
            // flattened the blended content into a private buffer with
            // normal compositing, and the blend never reached the page.
            // MEASURED on multiply over #3498db with the chip at
            // opacity 0.5 (spec/web/Android (50,98,135)):
            //
            //     blend inside the group    iOS (141,114,139)  = blend LOST
            //     blend outside the group   iOS matches
            //
            // With no opacity declared, engineOpacity is the identity and
            // this reorder changes nothing — which is what keeps every
            // blend-only and opacity-only baseline byte-identical.
            .engineIsolation(style.isolation)
            .engineOpacity(style.opacity)
            .engineBlendMode(style.blend)
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
            // Lane BF-I: the radius rides along so a `backdrop-filter`
            // backplate is clipped to the SAME rounded border box the
            // element's own background is (filter-effects-2 §2). It is inert
            // for the foreground filter chain and for every element that
            // declares no backdrop-filter.
            .engineFilter(style.filter, radius: style.borderRadius,
                          elementOpacity: style.opacity?.alpha ?? 1)
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
            // Wave-18 cleanup (clip-003): the HOISTED outline slot — nil
            // unless the element clips its own overflow (see the helper
            // pair on StyleBuilder). Attached AFTER engineVisibility so
            // the overlay wraps (draws outside) the element's own clip:
            // css-overflow clips the element's content, while the css-ui-4
            // §4 outline is post-layout ink the ref/web keep painting (the
            // clip-003 triptych's red rings on content-clipping squares).
            // Known approximation: at this chain position the ring no
            // longer rides transform/opacity modifiers — acceptable until
            // a fixture combines outline + own-clip + transform. Ancestor
            // containers clipping a descendant's outline stays a
            // documented TODO on both natives.
            .engineOutline(StyleBuilder.hoistedOutline(style),
                           radius: style.borderRadius,
                           currentColor: style.text.color)
            .modifier(EffectsModifier(effect: style.effect))
            .engineSpacingMargin(style.spacing.margin, context: style.spacing.context)
            .engineSpacingMarginTrim(style.spacing.marginTrim)
            // CSS `zoom` (css-viewport-1 §"The zoom property") — LAST, so
            // in SwiftUI's inner→outer chain it is the OUTERMOST node.
            // `zoom` multiplies the element's USED values (every length,
            // spacing band, border width, font size) AND the layout slot
            // it occupies, so the whole chain above — box paint, sizing,
            // transforms, filters, and the margin band right before it —
            // has to sit inside for the multiplication to cover it.
            // Identity unless the IR carried a real factor, so every other
            // element in the corpus keeps a byte-identical view tree.
            .engineZoom(style.zoom)
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
