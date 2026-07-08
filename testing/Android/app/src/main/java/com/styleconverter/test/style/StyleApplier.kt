package com.styleconverter.test.style

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.styleconverter.test.style.core.ir.IRProperty
import com.styleconverter.test.style.borders.BordersFacade
import com.styleconverter.test.style.borders.sides.AllBordersConfig
import com.styleconverter.test.style.borders.radius.BorderRadiusConfig
import com.styleconverter.test.style.animations.AnimationConfig
import com.styleconverter.test.style.animations.AnimationExtractor
import com.styleconverter.test.style.animations.TransitionConfig
import com.styleconverter.test.style.color.ColorApplier
import com.styleconverter.test.style.color.ColorConfig
import com.styleconverter.test.style.color.ColorExtractor
import com.styleconverter.test.style.columns.MultiColumnConfig
import com.styleconverter.test.style.columns.MultiColumnExtractor
import com.styleconverter.test.style.content.ContentExtractor
import com.styleconverter.test.style.content.CounterConfig
import com.styleconverter.test.style.content.QuotesConfig
import com.styleconverter.test.style.effects.EffectsFacade
import com.styleconverter.test.style.interactions.forms.FormStylingConfig
import com.styleconverter.test.style.interactions.forms.FormStylingExtractor
import com.styleconverter.test.style.images.ObjectFitConfig
import com.styleconverter.test.style.images.ObjectFitExtractor
import com.styleconverter.test.style.performance.BoxModelConfig
import com.styleconverter.test.style.performance.PerformanceConfig
import com.styleconverter.test.style.performance.PerformanceExtractor
import com.styleconverter.test.style.typography.text.TextExtractor
import com.styleconverter.test.style.typography.text.TextWrapConfig
import com.styleconverter.test.style.typography.text.WritingModeApplier
import com.styleconverter.test.style.typography.text.WritingModeConfig
import com.styleconverter.test.style.interactions.InteractionApplier
import com.styleconverter.test.style.interactions.InteractionConfig
import com.styleconverter.test.style.interactions.InteractionExtractor
import com.styleconverter.test.style.layout.LayoutFacade
import com.styleconverter.test.style.lists.ListStyleConfig
import com.styleconverter.test.style.lists.ListStyleExtractor
import com.styleconverter.test.style.scrolling.OverflowApplier
import com.styleconverter.test.style.scrolling.OverflowConfig
import com.styleconverter.test.style.scrolling.OverflowExtractor
import com.styleconverter.test.style.scrolling.ScrollApplier
import com.styleconverter.test.style.scrolling.ScrollConfig
import com.styleconverter.test.style.scrolling.ScrollExtractor
import com.styleconverter.test.style.table.TableConfig
import com.styleconverter.test.style.table.TableExtractor
import com.styleconverter.test.style.transforms.TransformApplier
import com.styleconverter.test.style.transforms.TransformConfig
import com.styleconverter.test.style.transforms.TransformExtractor
import com.styleconverter.test.style.typography.TypographyConfig
import com.styleconverter.test.style.typography.TypographyExtractor
import com.styleconverter.test.style.effects.mask.MaskApplier
import com.styleconverter.test.style.effects.mask.MaskConfig
import com.styleconverter.test.style.effects.mask.MaskExtractor
import com.styleconverter.test.style.borders.image.BorderImageConfig
import com.styleconverter.test.style.borders.image.BorderImageExtractor
import com.styleconverter.test.style.svg.SvgConfig
import com.styleconverter.test.style.svg.SvgExtractor
import com.styleconverter.test.style.container.ContainerQueryConfig
import com.styleconverter.test.style.container.ContainerQueryExtractor
import com.styleconverter.test.style.color.AccentConfig
import com.styleconverter.test.style.color.AccentExtractor
import com.styleconverter.test.style.layout.FloatConfig
import com.styleconverter.test.style.layout.FloatExtractor
import com.styleconverter.test.style.print.PrintConfig
import com.styleconverter.test.style.print.PrintExtractor
import com.styleconverter.test.style.typography.advanced.BaselineConfig
import com.styleconverter.test.style.typography.advanced.BaselineExtractor
import com.styleconverter.test.style.rendering.RenderingConfig
import com.styleconverter.test.style.rendering.RenderingExtractor
import com.styleconverter.test.style.shapes.ShapeConfig
import com.styleconverter.test.style.shapes.ShapeExtractor
import com.styleconverter.test.style.typography.ruby.RubyConfig
import com.styleconverter.test.style.typography.ruby.RubyExtractor
import com.styleconverter.test.style.layout.advanced.OffsetPathConfig
import com.styleconverter.test.style.layout.advanced.OffsetPathExtractor
import com.styleconverter.test.style.rendering.ZoomConfig
import com.styleconverter.test.style.rendering.ZoomExtractor
import com.styleconverter.test.style.transforms.Transform3DConfig
import com.styleconverter.test.style.transforms.Transform3DExtractor
import com.styleconverter.test.style.interactions.SpatialNavigationConfig
import com.styleconverter.test.style.interactions.SpatialNavigationExtractor
import com.styleconverter.test.style.typography.TextFormattingConfig
import com.styleconverter.test.style.typography.TextFormattingExtractor
import com.styleconverter.test.style.speech.SpeechConfig
import com.styleconverter.test.style.speech.SpeechExtractor
import com.styleconverter.test.style.regions.RegionFlowConfig
import com.styleconverter.test.style.regions.RegionFlowExtractor
import com.styleconverter.test.style.math.MathTypographyConfig
import com.styleconverter.test.style.math.MathTypographyExtractor
import com.styleconverter.test.style.scrolling.ScrollTimelineConfig
import com.styleconverter.test.style.scrolling.ScrollTimelineExtractor
import com.styleconverter.test.style.spacing.MarginTrimConfig
import com.styleconverter.test.style.spacing.MarginTrimExtractor
import com.styleconverter.test.style.background.BackgroundBoxConfig
import com.styleconverter.test.style.background.BackgroundBoxExtractor
import com.styleconverter.test.style.performance.IsolationApplier
import com.styleconverter.test.style.performance.IsolationConfig
import com.styleconverter.test.style.performance.IsolationExtractor
import com.styleconverter.test.style.typography.TextEmphasisConfig
import com.styleconverter.test.style.typography.FontVariantConfig
import com.styleconverter.test.style.typography.FontSynthesisConfig
import kotlinx.serialization.json.JsonElement

/**
 * Main facade for applying all CSS properties to Compose Modifiers.
 *
 * This is the entry point for the modular style system architecture.
 * It orchestrates all category-specific facades (layout, colors, borders, effects, transforms).
 *
 * ## Architecture
 *
 * ```
 * StyleApplier (this file)
 *     |
 *     +-- LayoutFacade (sizing, spacing, position, flex, grid)
 *     |       +-- SizingExtractor/Applier
 *     |       +-- SpacingExtractor/Applier
 *     |       +-- PositionExtractor/Applier
 *     |       +-- FlexExtractor
 *     |       +-- GridExtractor
 *     |
 *     +-- ColorExtractor/Applier (background, opacity, gradients)
 *     |
 *     +-- BordersFacade (sides, radius, outline)
 *     |       +-- BorderSideExtractor/Applier
 *     |       +-- BorderRadiusExtractor/Applier
 *     |       +-- OutlineExtractor/Applier
 *     |
 *     +-- EffectsFacade (filters, backdrop, shadows, clip-path)
 *     |       +-- FilterExtractor/Applier
 *     |       +-- ShadowExtractor/Applier
 *     |       +-- ClipPathExtractor/Applier
 *     |
 *     +-- TransformExtractor/Applier (rotate, scale, translate)
 *     |
 *     +-- TypographyExtractor/Applier (font, text styling)
 *     |
 *     +-- OverflowExtractor/Applier (overflow behavior)
 *     |
 *     +-- ListStyleExtractor/Applier (list markers)
 * ```
 *
 * ## Usage
 *
 * ```kotlin
 * // Simple usage with IRProperty list
 * val modifier = StyleApplier.applyProperties(component.properties)
 *
 * // For more control, extract config first
 * val pairs = properties.map { it.type to it.data }
 * val config = StyleApplier.extractConfig(pairs)
 * val modifier = StyleApplier.applyConfig(Modifier, config)
 * ```
 *
 * ## Order of Application
 *
 * Modifiers are applied in a specific order that matches CSS cascading behavior:
 * 1. **Layout** (sizing, spacing, position) - Sets dimensions and spacing
 * 2. **Borders** (sides and radius) - Draws borders and clips corners
 * 3. **Colors** (background, opacity, gradients) - Fills backgrounds
 * 4. **Effects** (filters, shadows, clip-path) - Applies visual effects
 * 5. **Transforms** (rotate, scale, translate) - Visual transformations
 * 6. **Overflow** (scroll, clip) - Overflow behavior
 *
 * Note: Typography and Lists are extracted but not applied to modifiers -
 * they are used by text rendering components.
 *
 * This order ensures proper visual layering and that transforms don't affect
 * the layout of other elements.
 */
object StyleApplier {

    /**
     * Complete style configuration extracted from IR properties.
     *
     * This aggregates all category-specific configurations into a single
     * data class for easy passing and manipulation.
     */
    data class StyleConfig(
        val layout: LayoutFacade.LayoutConfig = LayoutFacade.LayoutConfig(),
        val colors: ColorConfig = ColorConfig(),
        val borders: BordersFacade.BordersConfig = BordersFacade.BordersConfig(
            sides = AllBordersConfig(),
            radius = BorderRadiusConfig()
        ),
        val effects: EffectsFacade.EffectsConfig = EffectsFacade.EffectsConfig(),
        val transforms: TransformConfig = TransformConfig(),
        val typography: TypographyConfig = TypographyConfig(),
        val overflow: OverflowConfig = OverflowConfig(),
        val lists: ListStyleConfig = ListStyleConfig(),
        val interactions: InteractionConfig = InteractionConfig(),
        val scroll: ScrollConfig = ScrollConfig(),
        val animations: AnimationConfig = AnimationConfig(),
        val transitions: TransitionConfig = TransitionConfig(),
        val tables: TableConfig = TableConfig(),
        val columns: MultiColumnConfig = MultiColumnConfig(),
        val forms: FormStylingConfig = FormStylingConfig(),
        // New configs
        val objectFit: ObjectFitConfig = ObjectFitConfig(),
        val boxModel: BoxModelConfig = BoxModelConfig(),
        val performance: PerformanceConfig = PerformanceConfig(),
        val counters: CounterConfig = CounterConfig(),
        val quotes: QuotesConfig = QuotesConfig(),
        val writingMode: WritingModeConfig = WritingModeConfig(),
        val textWrap: TextWrapConfig = TextWrapConfig(),
        // Additional configs
        val mask: MaskConfig = MaskConfig(),
        val borderImage: BorderImageConfig = BorderImageConfig(),
        val svg: SvgConfig = SvgConfig(),
        // Phase 2 configs
        val containerQuery: ContainerQueryConfig = ContainerQueryConfig(),
        val accent: AccentConfig = AccentConfig(),
        val float: FloatConfig = FloatConfig(),
        val print: PrintConfig = PrintConfig(),
        val baseline: BaselineConfig = BaselineConfig(),
        val rendering: RenderingConfig = RenderingConfig(),
        val shape: ShapeConfig = ShapeConfig(),
        val ruby: RubyConfig = RubyConfig(),
        val offsetPath: OffsetPathConfig = OffsetPathConfig(),
        // Phase 3 configs
        val zoom: ZoomConfig = ZoomConfig(),
        val transform3D: Transform3DConfig = Transform3DConfig(),
        val spatialNavigation: SpatialNavigationConfig = SpatialNavigationConfig(),
        val textFormatting: TextFormattingConfig = TextFormattingConfig(),
        val speech: SpeechConfig = SpeechConfig(),
        val regionFlow: RegionFlowConfig = RegionFlowConfig(),
        val mathTypography: MathTypographyConfig = MathTypographyConfig(),
        val scrollTimeline: ScrollTimelineConfig = ScrollTimelineConfig(),
        val marginTrim: MarginTrimConfig = MarginTrimConfig(),
        val backgroundBox: BackgroundBoxConfig = BackgroundBoxConfig(),
        // Typography sub-configs
        val textEmphasis: TextEmphasisConfig = TextEmphasisConfig(),
        val fontVariant: FontVariantConfig = FontVariantConfig(),
        val fontSynthesis: FontSynthesisConfig = FontSynthesisConfig(),
        // Phase 4 addition — CSS isolation (AUTO/ISOLATE). See IsolationConfig.
        val isolation: IsolationConfig = IsolationConfig()
    ) {
        /**
         * Returns true if any style properties are present.
         */
        val hasStyles: Boolean
            get() = layout.hasLayout ||
                    colors.hasColor ||
                    borders.hasBorders ||
                    effects.hasEffects ||
                    transforms.hasTransform ||
                    typography.hasTypography ||
                    overflow.hasOverflow ||
                    lists.hasListStyle ||
                    interactions.hasInteraction ||
                    scroll.hasScrollConfig ||
                    animations.hasAnimations ||
                    transitions.hasTransitions ||
                    tables.hasTableConfig ||
                    columns.hasMultiColumn ||
                    forms.hasFormStyling ||
                    objectFit.hasObjectFit ||
                    boxModel.hasBoxModelConfig ||
                    performance.hasPerformanceConfig ||
                    counters.hasCounters ||
                    quotes.hasQuotes ||
                    writingMode.hasWritingMode ||
                    textWrap.hasTextWrap ||
                    mask.hasMask ||
                    borderImage.hasBorderImage ||
                    svg.hasSvgProperties ||
                    containerQuery.hasContainerQuery ||
                    accent.hasAccentColor ||
                    float.hasFloatProperties ||
                    print.hasPrintProperties ||
                    baseline.hasBaselineProperties ||
                    rendering.hasRenderingProperties ||
                    shape.hasShapeProperties ||
                    ruby.hasRubyProperties ||
                    offsetPath.hasOffsetPathProperties ||
                    zoom.hasZoom ||
                    transform3D.hasTransform3D ||
                    spatialNavigation.hasSpatialNavigation ||
                    textFormatting.hasTextFormatting ||
                    speech.hasSpeech ||
                    regionFlow.hasRegionFlow ||
                    mathTypography.hasMathTypography ||
                    scrollTimeline.hasScrollTimeline ||
                    marginTrim.hasMarginTrim ||
                    backgroundBox.hasBackgroundBox ||
                    textEmphasis.hasEmphasis ||
                    fontVariant.hasFontVariant ||
                    fontSynthesis.hasFontSynthesis ||
                    isolation.hasIsolation
    }

    /**
     * Apply a list of IR properties to a Modifier.
     *
     * This is the primary entry point for most use cases. It extracts
     * all configurations from the properties and applies them in the
     * correct order.
     *
     * @param properties List of IR properties to apply
     * @return Modified Modifier with all applicable styles
     */
    fun applyProperties(properties: List<IRProperty>): Modifier {
        // Convert to type/data pairs for extractors
        val pairs = properties.map { it.type to it.data }

        // Extract all configurations
        val config = extractConfig(pairs)

        // Apply in correct order
        return applyConfig(Modifier, config)
    }

    /**
     * Extract complete style configuration from property pairs.
     *
     * Use this when you need access to the extracted configuration
     * before applying it, for example to inspect flex container settings
     * or to conditionally apply certain styles.
     *
     * @param properties List of (propertyType, data) pairs from IR
     * @return StyleConfig with all extracted configurations
     */
    fun extractConfig(properties: List<Pair<String, JsonElement?>>): StyleConfig {
        return StyleConfig(
            layout = LayoutFacade.extractConfig(properties),
            colors = ColorExtractor.extractColorConfig(properties),
            borders = BordersFacade.extractConfig(properties),
            effects = EffectsFacade.extractConfig(properties),
            transforms = TransformExtractor.extractTransformConfig(properties),
            typography = TypographyExtractor.extractTypographyConfig(properties),
            overflow = OverflowExtractor.extractOverflowConfig(properties),
            lists = ListStyleExtractor.extractListStyleConfig(properties),
            interactions = InteractionExtractor.extractInteractionConfig(properties),
            scroll = ScrollExtractor.extractScrollConfig(properties),
            animations = AnimationExtractor.extractAnimationConfig(properties),
            transitions = AnimationExtractor.extractTransitionConfig(properties),
            tables = TableExtractor.extractTableConfig(properties),
            columns = MultiColumnExtractor.extractMultiColumnConfig(properties),
            forms = FormStylingExtractor.extractFormConfig(properties),
            // New extractors
            objectFit = ObjectFitExtractor.extractObjectFitConfig(properties),
            boxModel = PerformanceExtractor.extractBoxModelConfig(properties),
            performance = PerformanceExtractor.extractPerformanceConfig(properties),
            counters = ContentExtractor.extractCounterConfig(properties),
            quotes = ContentExtractor.extractQuotesConfig(properties),
            writingMode = TextExtractor.extractWritingModeConfig(properties),
            textWrap = TextExtractor.extractTextWrapConfig(properties),
            // Additional extractors
            mask = MaskExtractor.extractMaskConfig(properties),
            borderImage = BorderImageExtractor.extractBorderImageConfig(properties),
            svg = SvgExtractor.extractSvgConfig(properties),
            // Phase 2 extractors
            containerQuery = ContainerQueryExtractor.extractContainerQueryConfig(properties),
            accent = AccentExtractor.extractAccentConfig(properties),
            float = FloatExtractor.extractFloatConfig(properties),
            print = PrintExtractor.extractPrintConfig(properties),
            baseline = BaselineExtractor.extractBaselineConfig(properties),
            rendering = RenderingExtractor.extractRenderingConfig(properties),
            shape = ShapeExtractor.extractShapeConfig(properties),
            ruby = RubyExtractor.extractRubyConfig(properties),
            offsetPath = OffsetPathExtractor.extractOffsetPathConfig(properties),
            // Phase 3 extractors
            zoom = ZoomExtractor.extractZoomConfig(properties),
            transform3D = Transform3DExtractor.extractTransform3DConfig(properties),
            spatialNavigation = SpatialNavigationExtractor.extractSpatialNavigationConfig(properties),
            textFormatting = TextFormattingExtractor.extractTextFormattingConfig(properties),
            speech = SpeechExtractor.extractSpeechConfig(properties),
            regionFlow = RegionFlowExtractor.extractRegionFlowConfig(properties),
            mathTypography = MathTypographyExtractor.extractMathTypographyConfig(properties),
            scrollTimeline = ScrollTimelineExtractor.extractScrollTimelineConfig(properties),
            marginTrim = MarginTrimExtractor.extractMarginTrimConfig(properties),
            backgroundBox = BackgroundBoxExtractor.extractBackgroundBoxConfig(properties),
            // Typography sub-extractors
            textEmphasis = TypographyExtractor.extractTextEmphasisConfig(properties),
            fontVariant = TypographyExtractor.extractFontVariantConfig(properties),
            fontSynthesis = TypographyExtractor.extractFontSynthesisConfig(properties),
            // Isolation is pulled from IsolationExtractor — keeps this module
            // self-contained and easy to unit test.
            isolation = IsolationExtractor.extractIsolationConfig(properties)
        )
    }

    /**
     * Apply style configuration to a modifier.
     *
     * The order of application matters for proper visual layering:
     * 1. Layout first (sets dimensions)
     * 2. Padding/margin (affects internal spacing)
     * 3. Position (affects placement)
     * 4. Borders (including radius for clipping)
     * 5. Colors/backgrounds
     * 6. Effects (filters, shadows, clip-path)
     * 7. Transforms (visual modifications)
     * 8. Overflow (clipping and scrolling)
     *
     * Note: Typography and Lists are not applied to modifiers directly -
     * they are used by text components and list rendering logic.
     *
     * @param modifier The base modifier to extend
     * @param config The complete style configuration
     * @return Modified Modifier with all styles applied
     */
    fun applyConfig(modifier: Modifier, config: StyleConfig): Modifier {
        var result = modifier

        // CSS rendering model: transforms, clip, opacity, and visibility apply to the
        // ENTIRE element (including background). In Compose, modifier order is outer→inner,
        // so these "whole-element" effects must come FIRST (outermost) in the chain.

        // 0. Isolation — `isolation: isolate` creates a new stacking/blending
        //    context. We apply it as the outermost modifier so the offscreen
        //    compositing boundary wraps everything this element paints,
        //    preventing mix-blend-mode inside from leaking through to ancestors.
        result = IsolationApplier.applyIsolation(result, config.isolation)

        // 1. Interactions (visibility/alpha) — outermost: hides entire element
        result = InteractionApplier.applyInteraction(result, config.interactions)

        // 1.5 Backface culling. CSS Transforms 2 §10: when
        // `backface-visibility: hidden` is set and the cumulative 3D
        // rotation flips the element away from the viewer, hide it.
        // We approximate by setting alpha=0 when rotateY OR rotateX
        // lands in (90°, 270°) modulo 360 — i.e. the element's normal
        // points away. The check has to be cross-applier (rotation lives
        // in TransformConfig, the visibility flag in InteractionConfig)
        // so we resolve it here rather than inside either applier.
        if (config.interactions.backfaceVisibility ==
                com.styleconverter.test.style.interactions.BackfaceVisibilityMode.HIDDEN) {
            val ry = (config.transforms.rotateY ?: 0f) +
                config.transforms.functions.sumOf {
                    when (it) {
                        is com.styleconverter.test.style.transforms.TransformFunction.RotateY -> it.degrees.toDouble()
                        else -> 0.0
                    }
                }.toFloat()
            val rx = (config.transforms.rotateX ?: 0f) +
                config.transforms.functions.sumOf {
                    when (it) {
                        is com.styleconverter.test.style.transforms.TransformFunction.RotateX -> it.degrees.toDouble()
                        else -> 0.0
                    }
                }.toFloat()
            // Normalise to [-180, 180] then check if absolute angle > 90.
            fun normalised(deg: Float): Float {
                var d = deg % 360f
                if (d > 180f) d -= 360f
                if (d < -180f) d += 360f
                return d
            }
            val flipped = kotlin.math.abs(normalised(ry)) > 90f ||
                          kotlin.math.abs(normalised(rx)) > 90f
            if (flipped) result = result.alpha(0f)
        }

        // 2. Transforms — rotate/scale/skew the entire element including bg
        result = TransformApplier.applyTransforms(result, config.transforms)

        // 2.5. Writing mode (vertical text, rotation)
        if (config.writingMode.hasWritingMode) {
            result = WritingModeApplier.applyWritingMode(result, config.writingMode)
        }

        // 3. Effects (filters, clip-path, shadows) — clip/filter the element
        result = EffectsFacade.apply(result, config.effects)

        // 3.5. Mask (applied after clip for proper compositing)
        if (config.mask.hasMask) {
            result = MaskApplier.applyMask(result, config.mask)
        }

        // 4. Layout — sizing + margin + position (NOT padding; see step 8).
        result = LayoutFacade.applyToModifier(result, config.layout)

        // 5. Borders (sides and radius). Radius's clip sits between the
        //    outer sizing frame and the background, so the rounded corners
        //    clip the full border-box including the padding band.
        result = BordersFacade.apply(result, config.borders)

        // 6. Colors (background, opacity). Background paints the full
        //    sized box, INCLUDING the padding region (because padding is
        //    applied in step 8 below, INSIDE bg in chain order).
        result = ColorApplier.applyColors(result, config.colors)

        // 7. Overflow (clipping and scrolling)
        result = OverflowApplier.applyOverflow(result, config.overflow)

        // 7.5. Scroll behavior (overscroll, snap)
        if (config.scroll.hasScrollConfig) {
            result = ScrollApplier.applyScroll(result, config.scroll)
        }

        // 8. Padding LAST (innermost). With padding deferred to the end of
        //    the chain, the modifier order becomes
        //      width(W) → radius/border → bg → overflow → padding(P)
        //    which gives border-box semantics (total = W) and lets the
        //    background fill the entire card including the padding band.
        //    This matches web's `* { box-sizing: border-box }` and the
        //    iOS chain (`engineSpacingPadding → engineSizing`). Without
        //    this, fixtures like Card_Complete (`width: 250; padding: 20`)
        //    rendered as 290 wide on Android with bg only filling the
        //    inner 250×content_h.
        result = LayoutFacade.applyPaddingOnly(result, config.layout)

        return result
    }

    /**
     * Apply only layout-related properties (sizing, spacing, position).
     *
     * Useful when you need to separate layout from visual styling,
     * for example when the layout is determined by a container.
     *
     * @param modifier The base modifier
     * @param config The style configuration
     * @return Modifier with only layout applied
     */
    fun applyLayoutOnly(modifier: Modifier, config: StyleConfig): Modifier {
        // Padding-last to mirror applyConfig's chain (border-box). Callers
        // who don't want padding can pass a config with empty padding.
        return LayoutFacade.applyPaddingOnly(
            LayoutFacade.applyToModifier(modifier, config.layout),
            config.layout
        )
    }

    /**
     * Apply only visual properties (colors, borders, effects, transforms).
     *
     * Useful when layout is handled separately by the container.
     *
     * @param modifier The base modifier
     * @param config The style configuration
     * @return Modifier with only visual styling applied
     */
    fun applyVisualsOnly(modifier: Modifier, config: StyleConfig): Modifier {
        var result = modifier
        result = BordersFacade.apply(result, config.borders)
        // Note: Border image requires BorderImageBox composable for async loading
        result = ColorApplier.applyColors(result, config.colors)
        result = EffectsFacade.apply(result, config.effects)
        if (config.mask.hasMask) {
            result = MaskApplier.applyMask(result, config.mask)
        }
        result = TransformApplier.applyTransforms(result, config.transforms)
        if (config.writingMode.hasWritingMode) {
            result = WritingModeApplier.applyWritingMode(result, config.writingMode)
        }
        return result
    }

    /**
     * Get a report of which properties are handled by the style system.
     *
     * @return Set of property type strings that are supported
     */
    fun getSupportedPropertyTypes(): Set<String> {
        return setOf(
            // Sizing
            "Width", "Height", "MinWidth", "MaxWidth", "MinHeight", "MaxHeight",
            "BlockSize", "InlineSize", "MinBlockSize", "MaxBlockSize", "MinInlineSize", "MaxInlineSize",
            // Spacing
            "PaddingTop", "PaddingRight", "PaddingBottom", "PaddingLeft",
            "PaddingBlockStart", "PaddingBlockEnd", "PaddingInlineStart", "PaddingInlineEnd",
            "MarginTop", "MarginRight", "MarginBottom", "MarginLeft",
            "MarginBlockStart", "MarginBlockEnd", "MarginInlineStart", "MarginInlineEnd",
            "Gap", "RowGap", "ColumnGap",
            // Position
            "Position", "Top", "Right", "Bottom", "Left", "ZIndex",
            "InsetBlockStart", "InsetBlockEnd", "InsetInlineStart", "InsetInlineEnd",
            // Flex
            "Display", "FlexDirection", "FlexWrap", "JustifyContent", "AlignItems", "AlignContent",
            "FlexGrow", "FlexShrink", "FlexBasis", "AlignSelf", "Order",
            // Grid
            "GridTemplateColumns", "GridTemplateRows", "GridTemplateAreas",
            "GridAutoColumns", "GridAutoRows", "GridAutoFlow",
            "GridColumnStart", "GridColumnEnd", "GridRowStart", "GridRowEnd",
            "GridColumn", "GridRow", "GridArea",
            "JustifyItems", "JustifySelf",
            // Colors
            "BackgroundColor", "Opacity", "BackgroundImage",
            // Borders
            "BorderWidth", "BorderStyle", "BorderColor",
            "BorderTopWidth", "BorderRightWidth", "BorderBottomWidth", "BorderLeftWidth",
            "BorderTopColor", "BorderRightColor", "BorderBottomColor", "BorderLeftColor",
            "BorderTopStyle", "BorderRightStyle", "BorderBottomStyle", "BorderLeftStyle",
            "BorderInlineStartWidth", "BorderInlineEndWidth", "BorderBlockStartWidth", "BorderBlockEndWidth",
            "BorderInlineStartColor", "BorderInlineEndColor", "BorderBlockStartColor", "BorderBlockEndColor",
            "BorderInlineStartStyle", "BorderInlineEndStyle", "BorderBlockStartStyle", "BorderBlockEndStyle",
            // Border radius
            "BorderTopLeftRadius", "BorderTopRightRadius", "BorderBottomLeftRadius", "BorderBottomRightRadius",
            "BorderStartStartRadius", "BorderStartEndRadius", "BorderEndStartRadius", "BorderEndEndRadius",
            // Outline
            "OutlineWidth", "OutlineStyle", "OutlineColor", "OutlineOffset",
            // Effects
            "Filter", "BackdropFilter", "BoxShadow",
            // Clip path
            "ClipPath", "Clip",
            // Transforms
            "Transform", "TransformOrigin", "Rotate", "Scale", "Translate",
            // Typography
            "FontFamily", "FontSize", "FontWeight", "FontStyle", "FontStretch",
            "LetterSpacing", "LineHeight", "TextAlign", "TextDecorationLine",
            "TextOverflow", "Color", "TextTransform", "TextIndent", "WordSpacing",
            "WhiteSpace", "LineClamp",
            // Overflow
            "Overflow", "OverflowX", "OverflowY", "OverflowBlock", "OverflowInline",
            "OverflowAnchor", "OverflowClipMargin",
            // Interactions
            "Visibility", "ContentVisibility", "PointerEvents", "UserSelect",
            "Cursor", "TouchAction", "Appearance", "BackfaceVisibility",
            // Lists
            "ListStyleType", "ListStylePosition", "ListStyleImage",
            // Writing mode
            "WritingMode", "TextOrientation", "Direction", "UnicodeBidi"
        )
    }

    /**
     * Check if a property type is supported by the style system.
     *
     * @param type The property type string
     * @return True if this property type is handled
     */
    fun isPropertySupported(type: String): Boolean {
        return type in getSupportedPropertyTypes() ||
               LayoutFacade.isLayoutProperty(type) ||
               BordersFacade.isBorderProperty(type) ||
               EffectsFacade.isEffectProperty(type) ||
               TypographyExtractor.isTypographyProperty(type) ||
               OverflowExtractor.isOverflowProperty(type) ||
               ListStyleExtractor.isListStyleProperty(type) ||
               InteractionExtractor.isInteractionProperty(type)
    }
}
