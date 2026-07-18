package com.styleconverter.runtime.layout

import androidx.compose.ui.Modifier
import com.styleconverter.runtime.layout.flexbox.FlexContainerConfig
import com.styleconverter.runtime.layout.flexbox.FlexExtractor
import com.styleconverter.runtime.layout.flexbox.FlexItemConfig
import com.styleconverter.runtime.layout.grid.GridConfig
import com.styleconverter.runtime.layout.grid.GridExtractor
import com.styleconverter.runtime.layout.position.PositionApplier
import com.styleconverter.runtime.layout.position.PositionConfig
import com.styleconverter.runtime.layout.position.PositionExtractor
import com.styleconverter.runtime.sizing.SizingApplier
import com.styleconverter.runtime.sizing.SizingConfig
import com.styleconverter.runtime.sizing.SizingExtractor
import com.styleconverter.runtime.spacing.GapConfig
import com.styleconverter.runtime.spacing.MarginConfig
import com.styleconverter.runtime.spacing.PaddingConfig
import com.styleconverter.runtime.spacing.SpacingApplier
import com.styleconverter.runtime.spacing.SpacingExtractor
import kotlinx.serialization.json.JsonElement

/**
 * Facade for all layout-related property handling.
 *
 * This provides a unified interface for extracting and applying:
 * - Sizing (width, height, min/max constraints)
 * - Spacing (padding, margin, gap)
 * - Position (position type, top/right/bottom/left, z-index)
 * - Flex (container and item properties)
 * - Grid (grid container and item properties)
 *
 * ## Usage
 * ```kotlin
 * val properties: List<Pair<String, JsonElement?>> = ...
 * val config = LayoutFacade.extractConfig(properties)
 * val modifier = LayoutFacade.applyToModifier(Modifier, config)
 * ```
 *
 * ## Order of Operations
 * 1. Sizing is applied first to establish dimensions
 * 2. Padding is applied next (inside the borders)
 * 3. Margin is applied as offset
 * 4. Position (offset and z-index) is applied last
 *
 * Note: Flex container/item configurations are extracted but not applied
 * directly to modifiers - they are used by the container rendering logic.
 */
object LayoutFacade {

    /**
     * Combined configuration for all layout properties.
     */
    data class LayoutConfig(
        val sizing: SizingConfig = SizingConfig(),
        val padding: PaddingConfig = PaddingConfig(),
        val margin: MarginConfig = MarginConfig(),
        val gap: GapConfig = GapConfig(),
        val position: PositionConfig = PositionConfig(),
        val flexContainer: FlexContainerConfig = FlexContainerConfig(),
        val flexItem: FlexItemConfig = FlexItemConfig(),
        val grid: GridConfig = GridConfig()
    ) {
        /**
         * Check if there are any layout properties to apply.
         */
        val hasLayout: Boolean
            get() = sizing.hasSizing ||
                padding.hasPadding ||
                margin.hasMargin ||
                gap.hasGap ||
                position.hasPosition ||
                grid.hasGrid

        /**
         * Check if this is a flex container.
         */
        val isFlexContainer: Boolean
            get() = flexContainer.isFlex

        /**
         * Check if this is a grid container.
         */
        val isGridContainer: Boolean
            get() = grid.hasGrid

        /**
         * Check if this element is absolutely positioned.
         */
        val isAbsolutelyPositioned: Boolean
            get() = position.isAbsolutelyPositioned
    }

    /**
     * Extract all layout configurations from a list of property type/data pairs.
     *
     * @param properties List of pairs where first is the property type (e.g., "Width")
     *                   and second is the JSON data for that property.
     * @return LayoutConfig containing all extracted layout configurations.
     */
    fun extractConfig(properties: List<Pair<String, JsonElement?>>): LayoutConfig {
        return LayoutConfig(
            sizing = SizingExtractor.extractSizingConfig(properties),
            padding = SpacingExtractor.extractPaddingConfig(properties),
            margin = SpacingExtractor.extractMarginConfig(properties),
            gap = SpacingExtractor.extractGapConfig(properties),
            position = PositionExtractor.extractPositionConfig(properties),
            flexContainer = FlexExtractor.extractContainerConfig(properties),
            flexItem = FlexExtractor.extractItemConfig(properties),
            grid = GridExtractor.extractGridConfig(properties)
        )
    }

    /**
     * Apply layout configurations to a modifier.
     *
     * IMPORTANT: this only applies sizing / margin / position. Padding is
     * deliberately NOT applied here — `StyleApplier.applyConfig` calls
     * [applyPaddingOnly] LAST, after borders / colors / overflow, so that
     * the resulting modifier chain is:
     *
     *   width(250) → radius+border → background → overflow → padding(20)
     *
     * with width OUTERMOST. In Compose, the leftmost modifier is the
     * outermost wrapper, so this gives:
     *   • border-box semantics (`width` includes padding) — total = 250.
     *   • background fills the entire 250×card-height area, including the
     *     padding band, because bg sits between width and padding.
     *   • border-radius clip applies to the full card outline.
     *
     * The previous order — padding-then-sizing inside this method — was
     * content-box (total = width + 2·padding) AND left the bg covering
     * only the inner width×content_h region (the padding band rendered
     * transparent). That made Card_Complete render as a 250×30 white
     * rectangle on Android while web/iOS measured 250×62-66.
     *
     * Flex container/item properties need to be handled by the container
     * rendering logic (Row, Column, Box, etc.).
     *
     * @param modifier The modifier to apply layout to.
     * @param config The combined layout configuration.
     * @return Modified modifier with sizing + margin + position applied.
     */
    fun applyToModifier(
        modifier: Modifier,
        config: LayoutConfig,
        // Optional CSS2 §8.3.1 collapsed-margin override for the block-axis
        // sides (see BlockMarginCollapse) — threaded from the composable
        // renderer because this static chain can't read CompositionLocals.
        collapsedMargin: com.styleconverter.runtime.spacing.CollapsedMargin? = null,
    ): Modifier {
        var result = modifier

        // Margin BEFORE sizing — in Compose chain order, leftmost = outermost.
        // For margin to inflate the LAYOUT box (so flex/column siblings see
        // the spacing), the padding emitted by MarginApplier must wrap the
        // size modifier. Applying margin first puts it at the OUTSIDE; the
        // subsequent applySizing chains size INSIDE the margin-padding.
        // Net effect: outer = size + margin, content area = size, sibling
        // children see outer for flex layout. CSS-correct.
        result = SpacingApplier.applyMargin(result, config.margin, collapsedMargin)

        // Apply sizing AFTER margin so size is INSIDE the margin-padding
        // wrap. This is what makes `width: 250 + padding: 20` render as a
        // 250-wide border-box (matching `* { box-sizing: border-box }` on
        // web and the iOS chain `engineSpacingPadding → engineSizing`).
        result = SizingApplier.applySizing(result, config.sizing)

        // Apply position (offset and z-index).
        result = PositionApplier.applyPosition(result, config.position)

        // Padding is intentionally deferred to [applyPaddingOnly], which
        // StyleApplier calls AFTER colors / overflow.
        return result
    }

    /**
     * Apply ONLY the padding side of [config], for the deferred-innermost
     * step described in [applyToModifier]. Idempotent / safe to call on a
     * config without padding (returns the modifier unchanged).
     */
    fun applyPaddingOnly(modifier: Modifier, config: LayoutConfig): Modifier {
        return SpacingApplier.applyPadding(modifier, config.padding)
    }

    /**
     * Apply only position-related modifiers (z-index and offset).
     * Useful when sizing/spacing is handled separately.
     *
     * @param modifier The base modifier
     * @param config The layout configuration
     * @return Modifier with only position applied
     */
    fun applyPositionOnly(modifier: Modifier, config: LayoutConfig): Modifier {
        return PositionApplier.applyPosition(modifier, config.position)
    }

    /**
     * Apply only sizing and spacing modifiers (no position).
     * Useful when position is handled by container logic.
     *
     * @param modifier The base modifier
     * @param config The layout configuration
     * @return Modifier with sizing and spacing applied
     */
    fun applySizingAndSpacing(modifier: Modifier, config: LayoutConfig): Modifier {
        var result = modifier
        result = SizingApplier.applySizing(result, config.sizing)
        result = SpacingApplier.applyPadding(result, config.padding)
        result = SpacingApplier.applyMargin(result, config.margin)
        return result
    }

    /**
     * Check if a property type is a layout-related property.
     *
     * @param type The property type string.
     * @return True if this is a layout property.
     */
    fun isLayoutProperty(type: String): Boolean {
        return SizingExtractor.isSizingProperty(type) ||
            SpacingExtractor.isSpacingProperty(type) ||
            PositionExtractor.isPositionProperty(type) ||
            isFlexProperty(type) ||
            GridExtractor.isGridProperty(type)
    }

    /**
     * Check if a property type is a flex-related property.
     */
    private fun isFlexProperty(type: String): Boolean {
        return type in FLEX_PROPERTIES
    }

    private val FLEX_PROPERTIES = setOf(
        "Display",
        "FlexDirection", "FlexWrap",
        "JustifyContent", "AlignItems", "AlignContent",
        "FlexGrow", "FlexShrink", "FlexBasis",
        "AlignSelf", "Order"
    )

    // --- Phase 7 step 1: style-engine LayoutConfig hook ---------------------
    //
    // The three methods below delegate to the new LayoutExtractor /
    // LayoutApplier scaffold. They are intentionally separate from the legacy
    // `extractConfig` / `applyToModifier` above — those remain untouched so
    // ComponentRenderer's existing code path works byte-identically.
    //
    // Later Phase 7 steps will migrate callers from the legacy pair to the
    // style-engine triplet, then (step 6) delete the legacy pair. Until then
    // both exist side by side.
    //
    // The method names deliberately don't collide with the legacy ones:
    //   extractConfig       -> legacy LayoutFacade.LayoutConfig
    //   extractLayoutConfig -> new style-engine LayoutConfig (top-level in package)

    /**
     * Phase 7 entrypoint — extract the style-engine [LayoutConfig] scaffold.
     *
     * Step 1: returns [LayoutConfig.Empty] (all nulls). See
     * [LayoutExtractor.extractLayoutConfig] for the contract.
     */
    fun extractLayoutConfig(
        properties: List<Pair<String, JsonElement?>>
    ): com.styleconverter.runtime.layout.LayoutConfig {
        return LayoutExtractor.extractLayoutConfig(properties)
    }

    /**
     * Phase 7 entrypoint — ask the style engine which Compose container this
     * component should render with. Step 1 always returns
     * [ContainerDecision.default] which signals "defer to the legacy renderer."
     */
    fun containerDecision(
        config: com.styleconverter.runtime.layout.LayoutConfig?
    ): ContainerDecision {
        // Null-safe so ComponentRenderer can pass null in failure paths.
        if (config == null) return ContainerDecision.default
        return LayoutApplier.containerDecision(config)
    }

    /**
     * Phase 7 entrypoint — child-level Modifier contribution (zIndex,
     * alignSelf, order, relative inset). Step 1 returns identity Modifier so
     * the legacy StyleApplier chain is unaffected.
     */
    fun childModifier(
        config: com.styleconverter.runtime.layout.LayoutConfig?
    ): Modifier {
        if (config == null) return Modifier
        return LayoutApplier.childModifier(config)
    }
}
