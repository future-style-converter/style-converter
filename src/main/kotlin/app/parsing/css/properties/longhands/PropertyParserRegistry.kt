package app.parsing.css.properties.longhands

import app.irmodels.IRProperty
import app.irmodels.properties.borders.*
import app.irmodels.properties.spacing.*
import app.irmodels.properties.layout.position.*
import app.irmodels.properties.scrolling.*
import app.irmodels.properties.typography.TextDecorationColorProperty
import app.irmodels.properties.typography.TextEmphasisColorProperty
import app.irmodels.properties.columns.ColumnRuleColorProperty
import app.parsing.css.properties.longhands.PropertyParserFactory.colorParser
import app.parsing.css.properties.longhands.PropertyParserFactory.paddingParser
import app.parsing.css.properties.longhands.PropertyParserFactory.marginParser
import app.parsing.css.properties.longhands.PropertyParserFactory.borderWidthParser
import app.parsing.css.properties.longhands.PropertyParserFactory.borderStyleParser
import app.parsing.css.properties.longhands.PropertyParserFactory.scrollPaddingParser
import app.parsing.css.properties.longhands.PropertyParserFactory.insetParser
import app.parsing.css.properties.longhands.color.*
import app.parsing.css.properties.longhands.background.*
import app.parsing.css.properties.longhands.borders.*
import app.parsing.css.properties.longhands.appearance.*
import app.parsing.css.properties.longhands.spacing.*
import app.parsing.css.properties.longhands.sizing.*
import app.parsing.css.properties.longhands.typography.*
import app.parsing.css.properties.longhands.layout.flexbox.*
import app.parsing.css.properties.longhands.layout.grid.*
import app.parsing.css.properties.longhands.layout.position.*
import app.parsing.css.properties.longhands.scrolling.*
import app.parsing.css.properties.longhands.animations.*
import app.parsing.css.properties.longhands.content.*
import app.parsing.css.properties.longhands.transforms.*
import app.parsing.css.properties.longhands.effects.*
import app.parsing.css.properties.longhands.columns.*
import app.parsing.css.properties.longhands.interactions.*
import app.parsing.css.properties.longhands.lists.*
import app.parsing.css.properties.longhands.table.*
import app.parsing.css.properties.longhands.images.*
import app.parsing.css.properties.longhands.performance.*
import app.parsing.css.properties.longhands.rendering.*
import app.parsing.css.properties.longhands.paging.*
import app.parsing.css.properties.longhands.svg.*
import app.parsing.css.properties.longhands.container.*
import app.parsing.css.properties.longhands.layout.advanced.*
import app.parsing.css.properties.longhands.layout.OverlayPropertyParser
import app.parsing.css.properties.longhands.layout.ReadingFlowPropertyParser
import app.parsing.css.properties.longhands.shapes.*
import app.parsing.css.properties.longhands.rhythm.*
import app.parsing.css.properties.longhands.global.*
import app.parsing.css.properties.longhands.regions.*
import app.parsing.css.properties.longhands.speech.*
import app.parsing.css.properties.longhands.counters.*
import app.parsing.css.properties.longhands.navigation.*
import app.parsing.css.properties.longhands.math.*
import app.parsing.css.properties.longhands.print.*
import app.parsing.css.properties.longhands.experimental.*

/**
 * Registry of longhand CSS property parsers.
 *
 * ## Purpose
 * Central mapping of CSS property names to their parser implementations.
 * Each parser transforms a raw CSS value string into a specific IRProperty type.
 *
 * ## Architecture
 * ```
 * CSS Value String → PropertyParserRegistry.parse() → IRProperty
 *                           ↓
 *                    parsers["property-name"]?.parse(value)
 *                           ↓
 *                    Specific property parser (e.g., FontSizePropertyParser)
 *                           ↓
 *                    IRProperty (e.g., FontSizeProperty)
 * ```
 *
 * ## Parser Types
 * 1. **Dedicated parsers**: Custom parser classes (e.g., FontSizePropertyParser)
 * 2. **Factory parsers**: Generated via PropertyParserFactory for common patterns:
 *    - colorParser() - for color properties
 *    - paddingParser() - for padding properties
 *    - marginParser() - for margin properties
 *    - borderWidthParser() - for border-width properties
 *    - borderStyleParser() - for border-style properties
 *    - scrollPaddingParser() - for scroll-padding properties
 *    - insetParser() - for position properties (top, left, etc.)
 *
 * ## Statistics
 * - 500+ registered parsers across 35+ categories
 * - Coverage: Typography, Layout, Colors, Borders, Animations, Transforms, etc.
 *
 * @see PropertyParser interface that all parsers implement
 * @see PropertyParserFactory for reusable parser factories
 * @see PropertiesParser for the orchestration layer
 */
object PropertyParserRegistry {

    private val parsers = mapOf<String, PropertyParser>(
        // Colors (11 parsers) - 4 using factory
        "color" to ColorPropertyParser,
        "background-color" to BackgroundColorPropertyParser,
        "border-top-color" to colorParser(::BorderTopColorProperty),
        "border-right-color" to colorParser(::BorderRightColorProperty),
        "border-bottom-color" to colorParser(::BorderBottomColorProperty),
        "border-left-color" to colorParser(::BorderLeftColorProperty),
        "opacity" to OpacityPropertyParser,
        "mix-blend-mode" to MixBlendModePropertyParser,
        "background-blend-mode" to BackgroundBlendModePropertyParser,
        "accent-color" to AccentColorPropertyParser,
        "color-scheme" to ColorSchemePropertyParser,

        // Spacing - Padding (4 parsers) - all using factory
        "padding-top" to paddingParser(::PaddingTopProperty),
        "padding-right" to paddingParser(::PaddingRightProperty),
        "padding-bottom" to paddingParser(::PaddingBottomProperty),
        "padding-left" to paddingParser(::PaddingLeftProperty),

        // Spacing - Margin (4 parsers) - all using factory
        "margin-top" to marginParser(::MarginTopProperty),
        "margin-right" to marginParser(::MarginRightProperty),
        "margin-bottom" to marginParser(::MarginBottomProperty),
        "margin-left" to marginParser(::MarginLeftProperty),

        // Spacing - Logical Margin & Padding (8 parsers) - all using factory
        "margin-block-start" to marginParser(::MarginBlockStartProperty),
        "margin-block-end" to marginParser(::MarginBlockEndProperty),
        "margin-inline-start" to marginParser(::MarginInlineStartProperty),
        "margin-inline-end" to marginParser(::MarginInlineEndProperty),
        "padding-block-start" to paddingParser(::PaddingBlockStartProperty),
        "padding-block-end" to paddingParser(::PaddingBlockEndProperty),
        "padding-inline-start" to paddingParser(::PaddingInlineStartProperty),
        "padding-inline-end" to paddingParser(::PaddingInlineEndProperty),

        // Spacing - Sizing (14 parsers)
        "width" to WidthPropertyParser,
        "max-width" to MaxWidthPropertyParser,
        "min-width" to MinWidthPropertyParser,
        "height" to HeightPropertyParser,
        "min-height" to MinHeightPropertyParser,
        "max-height" to MaxHeightPropertyParser,
        "box-sizing" to BoxSizingPropertyParser,
        "aspect-ratio" to AspectRatioPropertyParser,
        "block-size" to BlockSizePropertyParser,
        "inline-size" to InlineSizePropertyParser,
        "min-block-size" to MinBlockSizePropertyParser,
        "max-block-size" to MaxBlockSizePropertyParser,
        "min-inline-size" to MinInlineSizePropertyParser,
        "max-inline-size" to MaxInlineSizePropertyParser,

        // Typography (76 parsers)
        "font-size" to FontSizePropertyParser,
        "font-weight" to FontWeightPropertyParser,
        "text-align" to TextAlignPropertyParser,
        "line-height" to LineHeightPropertyParser,
        "letter-spacing" to LetterSpacingPropertyParser,
        "word-spacing" to WordSpacingPropertyParser,
        "text-transform" to TextTransformPropertyParser,
        "white-space" to WhiteSpacePropertyParser,
        "vertical-align" to VerticalAlignPropertyParser,
        "font-kerning" to FontKerningPropertyParser,
        "overflow-wrap" to OverflowWrapPropertyParser,
        "tab-size" to TabSizePropertyParser,
        "caret-color" to CaretColorPropertyParser,
        "line-clamp" to LineClampPropertyParser,
        "orphans" to OrphansPropertyParser,
        "widows" to WidowsPropertyParser,
        "line-break" to LineBreakPropertyParser,
        "font-variant-numeric" to FontVariantNumericPropertyParser,
        "font-variant-ligatures" to FontVariantLigaturesPropertyParser,
        "font-variant-position" to FontVariantPositionPropertyParser,
        "font-variant-east-asian" to FontVariantEastAsianPropertyParser,
        "font-variant-emoji" to FontVariantEmojiPropertyParser,
        "font-synthesis-weight" to FontSynthesisWeightPropertyParser,
        "font-synthesis-style" to FontSynthesisStylePropertyParser,
        "font-synthesis-small-caps" to FontSynthesisSmallCapsPropertyParser,
        "font-size-adjust" to FontSizeAdjustPropertyParser,
        "font-optical-sizing" to FontOpticalSizingPropertyParser,
        "font-feature-settings" to FontFeatureSettingsPropertyParser,
        "font-variation-settings" to FontVariationSettingsPropertyParser,
        "text-rendering" to TextRenderingPropertyParser,
        "text-decoration-skip-ink" to TextDecorationSkipInkPropertyParser,
        "text-emphasis-style" to TextEmphasisStylePropertyParser,
        "text-emphasis-color" to colorParser(::TextEmphasisColorProperty),
        "text-emphasis-position" to TextEmphasisPositionPropertyParser,
        "text-combine-upright" to TextCombineUprightPropertyParser,
        "text-size-adjust" to TextSizeAdjustPropertyParser,
        "hanging-punctuation" to HangingPunctuationPropertyParser,
        "ruby-position" to RubyPositionPropertyParser,
        "ruby-align" to RubyAlignPropertyParser,
        "ruby-merge" to RubyMergePropertyParser,
        "initial-letter" to InitialLetterPropertyParser,
        "initial-letter-align" to InitialLetterAlignPropertyParser,
        "hyphenate-character" to HyphenateCharacterPropertyParser,
        "hyphenate-limit-chars" to HyphenateLimitCharsPropertyParser,
        "hyphenate-limit-last" to HyphenateLimitLastPropertyParser,
        "hyphenate-limit-lines" to HyphenateLimitLinesPropertyParser,
        "hyphenate-limit-zone" to HyphenateLimitZonePropertyParser,
        "text-wrap" to TextWrapPropertyParser,
        "text-wrap-mode" to TextWrapModePropertyParser,
        "text-wrap-style" to TextWrapStylePropertyParser,
        "text-space-collapse" to TextSpaceCollapsePropertyParser,
        "text-space-trim" to TextSpaceTrimPropertyParser,
        "alignment-baseline" to AlignmentBaselinePropertyParser,
        "dominant-baseline" to DominantBaselinePropertyParser,
        "baseline-shift" to BaselineShiftPropertyParser,
        "font-display" to FontDisplayPropertyParser,
        "text-box-edge" to TextBoxEdgePropertyParser,
        "text-box-trim" to TextBoxTrimPropertyParser,
        "line-grid" to LineGridPropertyParser,
        "line-snap" to LineSnapPropertyParser,
        "max-lines" to MaxLinesPropertyParser,
        "line-height-step" to LineHeightStepPropertyParser,
        "text-anchor" to TextAnchorPropertyParser,
        "text-group-align" to TextGroupAlignPropertyParser,
        "vertical-align-last" to VerticalAlignLastPropertyParser,
        "text-autospace" to TextAutospacePropertyParser,
        "word-space-transform" to WordSpaceTransformPropertyParser,
        "glyph-orientation-vertical" to GlyphOrientationVerticalPropertyParser,
        "glyph-orientation-horizontal" to GlyphOrientationHorizontalPropertyParser,
        "kerning" to KerningPropertyParser,
        "baseline-source" to BaselineSourcePropertyParser,
        "dominant-baseline-adjust" to DominantBaselineAdjustPropertyParser,
        "font-language-override" to FontLanguageOverridePropertyParser,
        "font-palette" to FontPalettePropertyParser,
        "font-min-size" to FontMinSizePropertyParser,
        "font-max-size" to FontMaxSizePropertyParser,
        "font-named-instance" to FontNamedInstancePropertyParser,

        // Border widths (4 parsers) - all using factory
        "border-top-width" to borderWidthParser(::BorderTopWidthProperty),
        "border-right-width" to borderWidthParser(::BorderRightWidthProperty),
        "border-bottom-width" to borderWidthParser(::BorderBottomWidthProperty),
        "border-left-width" to borderWidthParser(::BorderLeftWidthProperty),

        // Border styles (5 parsers) - 4 using factory
        "border-style" to BorderStylePropertyParser,
        "border-top-style" to borderStyleParser(::BorderTopStyleProperty),
        "border-right-style" to borderStyleParser(::BorderRightStyleProperty),
        "border-bottom-style" to borderStyleParser(::BorderBottomStyleProperty),
        "border-left-style" to borderStyleParser(::BorderLeftStyleProperty),

        // Border radius (4 parsers)
        "border-top-left-radius" to BorderTopLeftRadiusPropertyParser,
        "border-top-right-radius" to BorderTopRightRadiusPropertyParser,
        "border-bottom-right-radius" to BorderBottomRightRadiusPropertyParser,
        "border-bottom-left-radius" to BorderBottomLeftRadiusPropertyParser,

        // Border image (5 parsers)
        "border-image-source" to BorderImageSourcePropertyParser,
        "border-image-slice" to BorderImageSlicePropertyParser,
        "border-image-width" to BorderImageWidthPropertyParser,
        "border-image-outset" to BorderImageOutsetPropertyParser,
        "border-image-repeat" to BorderImageRepeatPropertyParser,

        // Logical borders - Color (4 parsers) - all using factory
        "border-block-start-color" to colorParser(::BorderBlockStartColorProperty),
        "border-block-end-color" to colorParser(::BorderBlockEndColorProperty),
        "border-inline-start-color" to colorParser(::BorderInlineStartColorProperty),
        "border-inline-end-color" to colorParser(::BorderInlineEndColorProperty),

        // Logical borders - Style (4 parsers) - all using factory
        "border-block-start-style" to borderStyleParser(::BorderBlockStartStyleProperty),
        "border-block-end-style" to borderStyleParser(::BorderBlockEndStyleProperty),
        "border-inline-start-style" to borderStyleParser(::BorderInlineStartStyleProperty),
        "border-inline-end-style" to borderStyleParser(::BorderInlineEndStyleProperty),

        // Logical borders - Width (4 parsers) - all using factory
        "border-block-start-width" to borderWidthParser(::BorderBlockStartWidthProperty),
        "border-block-end-width" to borderWidthParser(::BorderBlockEndWidthProperty),
        "border-inline-start-width" to borderWidthParser(::BorderInlineStartWidthProperty),
        "border-inline-end-width" to borderWidthParser(::BorderInlineEndWidthProperty),

        // Outline (4 parsers) - 1 using factory
        "outline-style" to borderStyleParser(::OutlineStyleProperty),
        "outline-width" to OutlineWidthPropertyParser,
        "outline-color" to colorParser(::OutlineColorProperty),
        "outline-offset" to OutlineOffsetPropertyParser,

        // Layout - Display & Position (11 parsers) - 8 using factory
        "display" to DisplayPropertyParser,
        "position" to PositionPropertyParser,
        "top" to insetParser(::TopProperty),
        "right" to insetParser(::RightProperty),
        "bottom" to insetParser(::BottomProperty),
        "left" to insetParser(::LeftProperty),
        "z-index" to ZIndexPropertyParser,
        "inset-block-start" to insetParser(::InsetBlockStartProperty),
        "inset-block-end" to insetParser(::InsetBlockEndProperty),
        "inset-inline-start" to insetParser(::InsetInlineStartProperty),
        "inset-inline-end" to insetParser(::InsetInlineEndProperty),

        // Layout - Flexbox (13 parsers)
        "align-items" to AlignItemsPropertyParser,
        "justify-content" to JustifyContentPropertyParser,
        "flex-direction" to FlexDirectionPropertyParser,
        "flex-wrap" to FlexWrapPropertyParser,
        "flex-grow" to FlexGrowPropertyParser,
        "flex-shrink" to FlexShrinkPropertyParser,
        "flex-basis" to FlexBasisPropertyParser,
        "align-self" to AlignSelfPropertyParser,
        "align-content" to AlignContentPropertyParser,
        "row-gap" to RowGapPropertyParser,
        "column-gap" to ColumnGapPropertyParser,
        "gap" to GapPropertyParser,
        "order" to OrderPropertyParser,

        // Layout - Grid (13 parsers)
        "justify-items" to JustifyItemsPropertyParser,
        "justify-self" to JustifySelfPropertyParser,
        "grid-auto-flow" to GridAutoFlowPropertyParser,
        "grid-template-columns" to GridTemplateColumnsPropertyParser,
        "grid-template-rows" to GridTemplateRowsPropertyParser,
        "grid-template-areas" to GridTemplateAreasPropertyParser,
        "grid-auto-columns" to GridAutoColumnsPropertyParser,
        "grid-auto-rows" to GridAutoRowsPropertyParser,
        "grid-column-start" to GridColumnStartPropertyParser,
        "grid-column-end" to GridColumnEndPropertyParser,
        "grid-row-start" to GridRowStartPropertyParser,
        "grid-row-end" to GridRowEndPropertyParser,
        "grid-area" to GridAreaPropertyParser,

        // Layout - Float & Clear (2 parsers)
        "float" to FloatPropertyParser,
        "clear" to ClearPropertyParser,

        // Interaction (5 parsers)
        "cursor" to CursorPropertyParser,
        "pointer-events" to PointerEventsPropertyParser,
        "user-select" to UserSelectPropertyParser,
        "resize" to ResizePropertyParser,
        "scroll-behavior" to ScrollBehaviorPropertyParser,

        // Scrolling (25 parsers) - 8 scroll-padding using factory
        "scroll-margin-top" to ScrollMarginTopPropertyParser,
        "scroll-margin-right" to ScrollMarginRightPropertyParser,
        "scroll-margin-bottom" to ScrollMarginBottomPropertyParser,
        "scroll-margin-left" to ScrollMarginLeftPropertyParser,
        "scroll-margin-block-start" to ScrollMarginBlockStartPropertyParser,
        "scroll-margin-block-end" to ScrollMarginBlockEndPropertyParser,
        "scroll-margin-inline-start" to ScrollMarginInlineStartPropertyParser,
        "scroll-margin-inline-end" to ScrollMarginInlineEndPropertyParser,
        "scroll-padding-top" to scrollPaddingParser(::ScrollPaddingTopProperty),
        "scroll-padding-right" to scrollPaddingParser(::ScrollPaddingRightProperty),
        "scroll-padding-bottom" to scrollPaddingParser(::ScrollPaddingBottomProperty),
        "scroll-padding-left" to scrollPaddingParser(::ScrollPaddingLeftProperty),
        "scroll-padding-block-start" to scrollPaddingParser(::ScrollPaddingBlockStartProperty),
        "scroll-padding-block-end" to scrollPaddingParser(::ScrollPaddingBlockEndProperty),
        "scroll-padding-inline-start" to scrollPaddingParser(::ScrollPaddingInlineStartProperty),
        "scroll-padding-inline-end" to scrollPaddingParser(::ScrollPaddingInlineEndProperty),
        "scroll-snap-type" to ScrollSnapTypePropertyParser,
        "scroll-snap-align" to ScrollSnapAlignPropertyParser,
        "scroll-snap-stop" to ScrollSnapStopPropertyParser,
        "overflow-anchor" to OverflowAnchorPropertyParser,
        "scrollbar-width" to ScrollbarWidthPropertyParser,
        "overscroll-behavior-x" to OverscrollBehaviorXPropertyParser,
        "overscroll-behavior-y" to OverscrollBehaviorYPropertyParser,
        "overscroll-behavior-block" to OverscrollBehaviorBlockPropertyParser,
        "overscroll-behavior-inline" to OverscrollBehaviorInlinePropertyParser,

        // Lists (4 parsers)
        "list-style-type" to ListStyleTypePropertyParser,
        "list-style-position" to ListStylePositionPropertyParser,
        "list-style-image" to ListStyleImagePropertyParser,
        "quotes" to QuotesPropertyParser,

        // Effects (9 parsers)
        "box-shadow" to BoxShadowPropertyParser,
        "transform" to TransformPropertyParser,
        "filter" to FilterPropertyParser,
        "backdrop-filter" to BackdropFilterPropertyParser,
        "clip-path" to ClipPathPropertyParser,
        "mask-image" to MaskImagePropertyParser,
        "mask-size" to MaskSizePropertyParser,
        "mask-repeat" to MaskRepeatPropertyParser,
        "mask-position" to MaskPositionPropertyParser,

        // Transforms (3 parsers)
        "transform-origin" to TransformOriginPropertyParser,
        "transform-style" to TransformStylePropertyParser,
        "backface-visibility" to BackfaceVisibilityPropertyParser,

        // Table (5 parsers)
        "table-layout" to TableLayoutPropertyParser,
        "border-collapse" to BorderCollapsePropertyParser,
        "caption-side" to CaptionSidePropertyParser,
        "empty-cells" to EmptyCellsPropertyParser,
        "border-spacing" to BorderSpacingPropertyParser,

        // Effects - Overflow & Visibility (3 parsers)
        "overflow-x" to OverflowXPropertyParser,
        "overflow-y" to OverflowYPropertyParser,
        "visibility" to VisibilityPropertyParser,

        // Background (8 parsers)
        "background-image" to BackgroundImagePropertyParser,
        "background-position" to BackgroundPositionPropertyParser,
        "background-position-x" to BackgroundPositionXPropertyParser,
        "background-position-y" to BackgroundPositionYPropertyParser,
        "background-size" to BackgroundSizePropertyParser,
        "background-repeat" to BackgroundRepeatPropertyParser,
        "background-clip" to BackgroundClipPropertyParser,
        "background-origin" to BackgroundOriginPropertyParser,
        "background-attachment" to BackgroundAttachmentPropertyParser,

        // Typography - Text & Font (12 parsers)
        "font-style" to FontStylePropertyParser,
        "font-stretch" to FontStretchPropertyParser,
        "word-break" to WordBreakPropertyParser,
        "hyphens" to HyphensPropertyParser,
        "text-overflow" to TextOverflowPropertyParser,
        "text-indent" to TextIndentPropertyParser,
        "writing-mode" to WritingModePropertyParser,
        "font-family" to FontFamilyPropertyParser,
        "direction" to DirectionPropertyParser,
        "word-wrap" to WordWrapPropertyParser,

        // Images (2 parsers)
        "object-fit" to ObjectFitPropertyParser,
        "object-position" to ObjectPositionPropertyParser,

        // Animations (8 parsers)
        "animation-name" to AnimationNamePropertyParser,
        "animation-duration" to AnimationDurationPropertyParser,
        "animation-delay" to AnimationDelayPropertyParser,
        "animation-timing-function" to AnimationTimingFunctionPropertyParser,
        "animation-fill-mode" to AnimationFillModePropertyParser,
        "animation-direction" to AnimationDirectionPropertyParser,
        "animation-play-state" to AnimationPlayStatePropertyParser,
        "animation-iteration-count" to AnimationIterationCountPropertyParser,

        // Transitions (4 parsers)
        "transition-property" to TransitionPropertyPropertyParser,
        "transition-duration" to TransitionDurationPropertyParser,
        "transition-delay" to TransitionDelayPropertyParser,
        "transition-timing-function" to TransitionTimingFunctionPropertyParser,

        // Text Decoration (4 parsers)
        "text-decoration-line" to TextDecorationLinePropertyParser,
        "text-decoration-color" to colorParser(::TextDecorationColorProperty),
        "text-decoration-style" to TextDecorationStylePropertyParser,
        "text-decoration-thickness" to TextDecorationThicknessPropertyParser,

        // Typography Advanced (7 parsers)
        "text-align-last" to TextAlignLastPropertyParser,
        "text-orientation" to TextOrientationPropertyParser,
        "text-justify" to TextJustifyPropertyParser,
        "unicode-bidi" to UnicodeBidiPropertyParser,
        "text-shadow" to TextShadowPropertyParser,
        "text-underline-offset" to TextUnderlineOffsetPropertyParser,
        "text-underline-position" to TextUnderlinePositionPropertyParser,

        // Columns (7 parsers)
        "column-span" to ColumnSpanPropertyParser,
        "column-fill" to ColumnFillPropertyParser,
        "column-count" to ColumnCountPropertyParser,
        "column-width" to ColumnWidthPropertyParser,
        "column-rule-width" to ColumnRuleWidthPropertyParser,
        "column-rule-style" to ColumnRuleStylePropertyParser,
        "column-rule-color" to colorParser(::ColumnRuleColorProperty),

        // Misc (8 parsers)
        "image-rendering" to ImageRenderingPropertyParser,
        "touch-action" to TouchActionPropertyParser,
        "appearance" to AppearancePropertyParser,
        "overscroll-behavior" to OverscrollBehaviorPropertyParser,
        "will-change" to WillChangePropertyParser,
        "contain" to ContainPropertyParser,
        "isolation" to IsolationPropertyParser,
        "color-adjust" to ColorAdjustPropertyParser,

        // Content (1 parser)
        "content" to ContentPropertyParser,

        // 3D Transforms (2 parsers)
        "perspective" to PerspectivePropertyParser,
        "perspective-origin" to PerspectiveOriginPropertyParser,

        // Mask Advanced (4 parsers)
        "mask-clip" to MaskClipPropertyParser,
        "mask-origin" to MaskOriginPropertyParser,
        "mask-composite" to MaskCompositePropertyParser,
        "mask-mode" to MaskModePropertyParser,

        // Scrollbar Advanced (2 parsers)
        "scrollbar-color" to ScrollbarColorPropertyParser,
        "scrollbar-gutter" to ScrollbarGutterPropertyParser,

        // Clipping (1 parser)
        "clip" to ClipPropertyParser,

        // Page Breaking (6 parsers)
        "break-before" to BreakBeforePropertyParser,
        "break-after" to BreakAfterPropertyParser,
        "break-inside" to BreakInsidePropertyParser,
        "page-break-before" to PageBreakBeforePropertyParser,
        "page-break-after" to PageBreakAfterPropertyParser,
        "page-break-inside" to PageBreakInsidePropertyParser,

        // Rendering (1 parser)
        "content-visibility" to ContentVisibilityPropertyParser,

        // Typography - Additional (1 parser)
        "font-variant-caps" to FontVariantCapsPropertyParser,

        // SVG Properties (24 parsers)
        "fill" to FillPropertyParser,
        "fill-opacity" to FillOpacityPropertyParser,
        "fill-rule" to FillRulePropertyParser,
        "stroke" to StrokePropertyParser,
        "stroke-width" to StrokeWidthPropertyParser,
        "stroke-opacity" to StrokeOpacityPropertyParser,
        "stroke-dasharray" to StrokeDasharrayPropertyParser,
        "stroke-dashoffset" to StrokeDashoffsetPropertyParser,
        "stroke-linecap" to StrokeLinecapPropertyParser,
        "stroke-linejoin" to StrokeLinejoinPropertyParser,
        "stroke-miterlimit" to StrokeMiterlimitPropertyParser,
        "marker" to MarkerPropertyParser,
        "marker-start" to MarkerStartPropertyParser,
        "marker-mid" to MarkerMidPropertyParser,
        "marker-end" to MarkerEndPropertyParser,
        "marker-side" to MarkerSidePropertyParser,
        "paint-order" to PaintOrderPropertyParser,
        "shape-rendering" to ShapeRenderingPropertyParser,
        "vector-effect" to VectorEffectPropertyParser,
        "stop-color" to StopColorPropertyParser,
        "stop-opacity" to StopOpacityPropertyParser,
        "lighting-color" to LightingColorPropertyParser,
        "buffered-rendering" to BufferedRenderingPropertyParser,
        "enable-background" to EnableBackgroundPropertyParser,

        // Container Queries (3 parsers)
        "container" to ContainerPropertyParser,
        "container-name" to ContainerNamePropertyParser,
        "container-type" to ContainerTypePropertyParser,

        // Offset/Motion Path (1 parser)
        "offset" to OffsetPropertyParser,

        // Shapes (1 parser)
        "shape-outside" to ShapeOutsidePropertyParser,

        // Transforms - Individual (1 parser)
        "rotate" to RotatePropertyParser,

        // Scroll Start (5 parsers)
        "scroll-start" to ScrollStartPropertyParser,
        "scroll-start-x" to ScrollStartXPropertyParser,
        "scroll-start-y" to ScrollStartYPropertyParser,
        "scroll-start-block" to ScrollStartBlockPropertyParser,
        "scroll-start-inline" to ScrollStartInlinePropertyParser,

        // Animations Advanced (6 parsers)
        "animation-composition" to AnimationCompositionPropertyParser,
        "animation-timeline" to AnimationTimelinePropertyParser,
        "animation-range-start" to AnimationRangeStartPropertyParser,
        "animation-range-end" to AnimationRangeEndPropertyParser,
        "transition-behavior" to TransitionBehaviorPropertyParser,
        "view-transition-name" to ViewTransitionNamePropertyParser,
        "timeline-scope" to TimelineScopePropertyParser,

        // Block Step / Rhythm (5 parsers)
        "block-step" to BlockStepPropertyParser,
        "block-step-align" to BlockStepAlignPropertyParser,
        "block-step-insert" to BlockStepInsertPropertyParser,
        "block-step-round" to BlockStepRoundPropertyParser,
        "block-step-size" to BlockStepSizePropertyParser,

        // Background - Logical Position (2 parsers)
        "background-position-block" to BackgroundPositionBlockPropertyParser,
        "background-position-inline" to BackgroundPositionInlinePropertyParser,

        // Contain Intrinsic (5 parsers)
        "contain-intrinsic-size" to ContainIntrinsicSizePropertyParser,
        "contain-intrinsic-width" to ContainIntrinsicWidthPropertyParser,
        "contain-intrinsic-height" to ContainIntrinsicHeightPropertyParser,
        "contain-intrinsic-block-size" to ContainIntrinsicBlockSizePropertyParser,
        "contain-intrinsic-inline-size" to ContainIntrinsicInlineSizePropertyParser,

        // SVG Flood (2 parsers)
        "flood-color" to FloodColorPropertyParser,
        "flood-opacity" to FloodOpacityPropertyParser,

        // Interactions Additional (1 parser)
        "caret-shape" to CaretShapePropertyParser,

        // Spacing Additional (1 parser)
        "margin-break" to MarginBreakPropertyParser,

        // Position Advanced (1 parser)
        "position-try-fallbacks" to PositionTryFallbacksPropertyParser,

        // Typography Additional (4 parsers)
        "ruby-overhang" to RubyOverhangPropertyParser,
        "block-ellipsis" to BlockEllipsisPropertyParser,
        "white-space-collapse" to WhiteSpaceCollapsePropertyParser,

        // Shapes Additional (1 parser)
        "shape-inside" to ShapeInsidePropertyParser,

        // Rendering Additional (1 parser)
        "zoom" to ZoomPropertyParser,

        // Global (1 parser)
        "all" to AllPropertyParser,

        // Counter properties (3 parsers)
        "counter-increment" to CounterIncrementPropertyParser,
        "counter-reset" to CounterResetPropertyParser,
        "counter-set" to CounterSetPropertyParser,

        // Navigation (4 parsers)
        "nav-up" to NavUpPropertyParser,
        "nav-down" to NavDownPropertyParser,
        "nav-left" to NavLeftPropertyParser,
        "nav-right" to NavRightPropertyParser,

        // Math (3 parsers)
        "math-style" to MathStylePropertyParser,
        "math-depth" to MathDepthPropertyParser,
        "math-shift" to MathShiftPropertyParser,

        // Print properties (13 parsers)
        "bleed" to BleedPropertyParser,
        "bookmark-label" to BookmarkLabelPropertyParser,
        "bookmark-level" to BookmarkLevelPropertyParser,
        "bookmark-state" to BookmarkStatePropertyParser,
        "bookmark-target" to BookmarkTargetPropertyParser,
        "footnote-display" to FootnoteDisplayPropertyParser,
        "footnote-policy" to FootnotePolicyPropertyParser,
        "leader" to LeaderPropertyParser,
        "marks" to MarksPropertyParser,
        "page" to PagePropertyParser,
        "size" to SizePropertyParser,
        "print-color-adjust" to PrintColorAdjustPropertyParser,
        "image-orientation" to ImageOrientationPropertyParser,

        // Regions (10 parsers)
        "flow-into" to FlowIntoPropertyParser,
        "flow-from" to FlowFromPropertyParser,
        "region-fragment" to RegionFragmentPropertyParser,
        "continue" to ContinuePropertyParser,
        "copy-into" to CopyIntoPropertyParser,
        "wrap-flow" to WrapFlowPropertyParser,
        "wrap-through" to WrapThroughPropertyParser,
        "wrap-before" to WrapBeforePropertyParser,
        "wrap-after" to WrapAfterPropertyParser,
        "wrap-inside" to WrapInsidePropertyParser,

        // Speech (27 parsers)
        "volume" to VolumePropertyParser,
        "speak" to SpeakPropertyParser,
        "speak-as" to SpeakAsPropertyParser,
        "pause" to PausePropertyParser,
        "pause-before" to PauseBeforePropertyParser,
        "pause-after" to PauseAfterPropertyParser,
        "rest" to RestPropertyParser,
        "rest-before" to RestBeforePropertyParser,
        "rest-after" to RestAfterPropertyParser,
        "cue" to CuePropertyParser,
        "cue-before" to CueBeforePropertyParser,
        "cue-after" to CueAfterPropertyParser,
        "voice-family" to VoiceFamilyPropertyParser,
        "voice-rate" to VoiceRatePropertyParser,
        "voice-pitch" to VoicePitchPropertyParser,
        "voice-range" to VoiceRangePropertyParser,
        "voice-stress" to VoiceStressPropertyParser,
        "voice-volume" to VoiceVolumePropertyParser,
        "voice-duration" to VoiceDurationPropertyParser,
        "voice-balance" to VoiceBalancePropertyParser,
        "pitch" to PitchPropertyParser,
        "pitch-range" to PitchRangePropertyParser,
        "richness" to RichnessPropertyParser,
        "stress" to StressPropertyParser,
        "speech-rate" to SpeechRatePropertyParser,
        "azimuth" to AzimuthPropertyParser,
        "elevation" to ElevationPropertyParser,

        // Experimental (3 parsers)
        "presentation-level" to PresentationLevelPropertyParser,
        "running" to RunningPropertyParser,
        "string-set" to StringSetPropertyParser,

        // Borders Additional (7 parsers)
        "border-boundary" to BorderBoundaryPropertyParser,
        "border-width" to BorderWidthPropertyParser,
        "box-decoration-break" to BoxDecorationBreakPropertyParser,
        "border-end-end-radius" to BorderEndEndRadiusPropertyParser,
        "border-end-start-radius" to BorderEndStartRadiusPropertyParser,
        "border-start-end-radius" to BorderStartEndRadiusPropertyParser,
        "border-start-start-radius" to BorderStartStartRadiusPropertyParser,

        // Rendering Additional (5 parsers)
        "color-interpolation" to ColorInterpolationPropertyParser,
        "color-interpolation-filters" to ColorInterpolationFiltersPropertyParser,
        "color-rendering" to ColorRenderingPropertyParser,
        "field-sizing" to FieldSizingPropertyParser,
        "forced-color-adjust" to ForcedColorAdjustPropertyParser,

        // Effects Additional (3 parsers)
        "clip-path-geometry-box" to ClipPathGeometryBoxPropertyParser,
        "clip-rule" to ClipRulePropertyParser,
        "overflow-clip-margin" to OverflowClipMarginPropertyParser,

        // Grid Additional (4 parsers)
        "align-tracks" to AlignTracksPropertyParser,
        "justify-tracks" to JustifyTracksPropertyParser,
        "masonry-auto-flow" to MasonryAutoFlowPropertyParser,
        "grid-auto-track" to GridAutoTrackPropertyParser,

        // Appearance Additional (1 parser)
        "appearance-variant" to AppearanceVariantPropertyParser,

        // Typography Additional - Font Synthesis (1 parser)
        "font-synthesis-position" to FontSynthesisPositionPropertyParser,
        "font-variant-alternates" to FontVariantAlternatesPropertyParser,

        // Shapes Additional (3 parsers)
        "shape-margin" to ShapeMarginPropertyParser,
        "shape-padding" to ShapePaddingPropertyParser,
        "shape-image-threshold" to ShapeImageThresholdPropertyParser,

        // Layout Advanced (7 parsers)
        "anchor-name" to AnchorNamePropertyParser,
        "anchor-scope" to AnchorScopePropertyParser,
        "position-anchor" to PositionAnchorPropertyParser,
        "position-area" to PositionAreaPropertyParser,
        "position-fallback" to PositionFallbackPropertyParser,
        "position-try" to PositionTryPropertyParser,
        "position-try-options" to PositionTryOptionsPropertyParser,
        "position-try-order" to PositionTryOrderPropertyParser,
        "position-visibility" to PositionVisibilityPropertyParser,
        "inset-area" to InsetAreaPropertyParser,
        "interpolate-size" to InterpolateSizePropertyParser,
        "overlay" to OverlayPropertyParser,
        "reading-flow" to ReadingFlowPropertyParser,

        // Effects - Overflow (2 parsers)
        "overflow-block" to OverflowBlockPropertyParser,
        "overflow-inline" to OverflowInlinePropertyParser,

        // Mask Position (2 parsers)
        "mask-position-x" to MaskPositionXPropertyParser,
        "mask-position-y" to MaskPositionYPropertyParser,
        "mask-type" to MaskTypePropertyParser,

        // Mask Border (6 parsers)
        "mask-border-mode" to MaskBorderModePropertyParser,
        "mask-border-outset" to MaskBorderOutsetPropertyParser,
        "mask-border-repeat" to MaskBorderRepeatPropertyParser,
        "mask-border-slice" to MaskBorderSlicePropertyParser,
        "mask-border-source" to MaskBorderSourcePropertyParser,
        "mask-border-width" to MaskBorderWidthPropertyParser,

        // Transforms Additional (4 parsers)
        "translate" to TranslatePropertyParser,
        "scale" to ScalePropertyParser,
        "transform-box" to TransformBoxPropertyParser,

        // Offset/Motion Path (5 parsers)
        "offset-anchor" to OffsetAnchorPropertyParser,
        "offset-distance" to OffsetDistancePropertyParser,
        "offset-path" to OffsetPathPropertyParser,
        "offset-position" to OffsetPositionPropertyParser,
        "offset-rotate" to OffsetRotatePropertyParser,

        // Scroll Start Target (5 parsers)
        "scroll-start-target" to ScrollStartTargetPropertyParser,
        "scroll-start-target-block" to ScrollStartTargetBlockPropertyParser,
        "scroll-start-target-inline" to ScrollStartTargetInlinePropertyParser,
        "scroll-start-target-x" to ScrollStartTargetXPropertyParser,
        "scroll-start-target-y" to ScrollStartTargetYPropertyParser,

        // Scroll Timeline (3 parsers)
        "scroll-timeline" to ScrollTimelinePropertyParser,
        "scroll-timeline-axis" to ScrollTimelineAxisPropertyParser,
        "scroll-timeline-name" to ScrollTimelineNamePropertyParser,

        // View Timeline (4 parsers)
        "view-timeline" to ViewTimelinePropertyParser,
        "view-timeline-axis" to ViewTimelineAxisPropertyParser,
        "view-timeline-inset" to ViewTimelineInsetPropertyParser,
        "view-timeline-name" to ViewTimelineNamePropertyParser,

        // Grid Template (1 parser)
        "grid-template" to GridTemplatePropertyParser,

        // Image Advanced (2 parsers)
        "image-rendering-quality" to ImageRenderingQualityPropertyParser,
        "image-resolution" to ImageResolutionPropertyParser,

        // Input (1 parser)
        "input-security" to InputSecurityPropertyParser,

        // Typography Additional (3 parsers)
        "text-align-all" to TextAlignAllPropertyParser,
        "text-decoration-skip" to TextDecorationSkipPropertyParser,
        "text-emphasis" to TextEmphasisPropertyParser,
        "text-spacing-trim" to TextSpacingTrimPropertyParser,
        "text-spacing" to TextSpacingPropertyParser,

        // Margin (1 parser)
        "margin-trim" to MarginTrimPropertyParser,

        // SVG Geometry (8 parsers)
        "cx" to CxPropertyParser,
        "cy" to CyPropertyParser,
        "r" to RPropertyParser,
        "rx" to RxPropertyParser,
        "ry" to RyPropertyParser,
        "x" to XPropertyParser,
        "y" to YPropertyParser,
        "d" to DPropertyParser,

        // Corner Shape (1 parser)
        "corner-shape" to CornerShapePropertyParser,

        // Dynamic Range (1 parser)
        "dynamic-range-limit" to DynamicRangeLimitPropertyParser,

        // Font Smooth (1 parser)
        "font-smooth" to FontSmoothPropertyParser,

        // Interactivity (2 parsers)
        "interactivity" to InteractivityPropertyParser,
        "caret" to CaretPropertyParser,

        // Object View Box (1 parser)
        "object-view-box" to ObjectViewBoxPropertyParser,

        // Reading Order (1 parser)
        "reading-order" to ReadingOrderPropertyParser,

        // Scroll Markers (2 parsers)
        "scroll-marker-group" to ScrollMarkerGroupPropertyParser,
        "scroll-target-group" to ScrollTargetGroupPropertyParser,

        // View Transition Class (1 parser)
        "view-transition-class" to ViewTransitionClassPropertyParser,
        "view-transition-group" to ViewTransitionGroupPropertyParser,

        // Webkit/vendor-prefixed properties (aliased to standard parsers)
        "-webkit-line-clamp" to LineClampPropertyParser,
        "-webkit-background-clip" to BackgroundClipPropertyParser,
        "-webkit-box-orient" to BoxOrientPropertyParser,

        // Animation range (shorthand that can also be used as longhand)
        "animation-range" to AnimationRangePropertyParser

        // Note: Shorthand properties are expanded by ShorthandRegistry before parsing,
        // so they do not need parsers here. Total: ~547 longhand parsers implemented.
    )

    /**
     * Parse a longhand property into a specific IRProperty instance.
     *
     * @param propertyName The property name (e.g., "background-color")
     * @param value The property value (e.g., "#FF0000")
     * @return The specific IRProperty subclass, or null if no parser exists or parsing fails
     */
    fun parse(propertyName: String, value: String): IRProperty? {
        val parser = parsers[propertyName] ?: return null
        return parser.parse(value)
    }

    /**
     * Check if a parser exists for this property.
     */
    fun hasParser(propertyName: String): Boolean {
        return propertyName in parsers
    }

    /**
     * Get the number of parsers registered.
     */
    fun parserCount(): Int = parsers.size

    /**
     * All property names with a registered parser.
     *
     * Exposed so ValidatorRegistryParityTest can assert every registered
     * name is also accepted by CssPropertyValidator — otherwise valid CSS
     * is silently dropped at validation time before its parser ever runs
     * (this drift happened for 7 properties; the test pins the class of bug).
     */
    fun registeredPropertyNames(): Set<String> = parsers.keys
}
