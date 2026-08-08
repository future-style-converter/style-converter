package com.styleconverter.runtime.performance

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies CSS performance-related properties to Compose.
 *
 * ## CSS Property Mapping
 * - contain: layout/paint → graphicsLayer with CompositingStrategy
 * - will-change: transform/opacity → graphicsLayer pre-allocation
 * - zoom → scale modifier
 *
 * ## Compose Mapping Strategy
 *
 * ### containment → CompositingStrategy
 * - `contain: paint` → CompositingStrategy.Offscreen (isolate paint operations)
 * - `contain: layout` → Hint to use LazyColumn/LazyRow
 * - `contain: size` → Hint for fixed intrinsic size
 * - `contain: strict/content` → Full isolation via graphicsLayer
 *
 * ### will-change → graphicsLayer
 * - `will-change: transform` → Pre-allocate transform layer
 * - `will-change: opacity` → Pre-allocate alpha layer
 * - `will-change: scroll-position` → Hint for LazyList optimization
 *
 * ### zoom → scale
 * - `zoom: 1.5` → Modifier.scale(1.5f)
 *
 * ## Limitations
 * - CSS containment is a hint; Compose handles this automatically in many cases
 * - `contain: style` has no Compose equivalent
 * - `will-change` is less important in Compose due to automatic optimization
 */
object PerformanceApplier {

    /**
     * Apply performance configuration to modifier.
     *
     * @param modifier Starting modifier
     * @param config PerformanceConfig with containment, will-change, zoom
     * @return Modified Modifier with performance optimizations
     */
    // The zoom branch below calls the deprecated paint-only helper on
    // purpose — this whole function is legacy and unreferenced by the
    // renderer (StyleApplier never chains it). Suppressed rather than
    // rewired so the deprecation stays visible on the helper itself,
    // where a future caller would read it.
    @Suppress("DEPRECATION")
    fun applyPerformance(modifier: Modifier, config: PerformanceConfig): Modifier {
        var result = modifier

        // Apply containment via graphicsLayer
        if (config.contain.hasContainment) {
            result = applyContainment(result, config.contain)
        }

        // Apply will-change hints via graphicsLayer
        if (config.willChange.hasWillChange) {
            result = applyWillChange(result, config.willChange)
        }

        // Apply zoom via scale
        if (!config.zoom.isNormal) {
            result = applyZoom(result, config.zoom)
        }

        return result
    }

    /**
     * Apply CSS contain property.
     *
     * `contain: paint` maps to CompositingStrategy.Offscreen which creates
     * an isolated graphics layer, similar to CSS paint containment.
     *
     * `contain: layout` hints that the element is isolated from layout
     * effects of descendants. In Compose, this is handled automatically
     * by recomposition scopes.
     *
     * @param modifier Starting modifier
     * @param config ContainConfig
     * @return Modified Modifier
     */
    fun applyContainment(modifier: Modifier, config: ContainConfig): Modifier {
        if (!config.hasContainment) return modifier

        // paint containment → offscreen compositing
        if (config.paint || config.isStrict || config.isContent) {
            return modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }

        // layout containment alone doesn't have a direct modifier equivalent
        // but we can still create a graphics layer for isolation
        if (config.layout) {
            return modifier.graphicsLayer {
                // Layout isolation - just create a layer
                compositingStrategy = CompositingStrategy.Auto
            }
        }

        return modifier
    }

    /**
     * Apply CSS will-change property.
     *
     * Pre-allocates graphics layers for properties that will change,
     * reducing jank during animations.
     *
     * @param modifier Starting modifier
     * @param config WillChangeConfig
     * @return Modified Modifier with pre-allocated layers
     */
    fun applyWillChange(modifier: Modifier, @Suppress("unused") config: WillChangeConfig): Modifier {
        // `will-change` is a compositor hint, not a visual property — the
        // browser/renderer is free to ignore it. Our SDUI runtime has no
        // pre-animation queue that would benefit from a pre-allocated
        // Compose RenderNode, and empirically `Modifier.graphicsLayer { clip
        // = false }` was collapsing component height (390×102 → 390×53) in
        // the Phase 12 audit (all 5 WillChange_* variants). iOS and Web both
        // treat will-change as an identity modifier, so matching their
        // behaviour eliminates the cross-platform divergence at zero cost.
        // If a future phase wires actual animation machinery into the
        // renderer, that's when we'd opt back into the pre-allocated layer
        // — with an explicit sizing modifier to prevent the collapse.
        return modifier
    }

    /**
     * Apply CSS zoom property.
     *
     * NOT THE RENDER PATH — do not chain this. `Modifier.scale` warps
     * PAINT only and leaves the layout slot unzoomed, which is precisely
     * the approximation css-viewport-1 `zoom` forbids (it multiplies used
     * values AND the slot). The real implementation is the dedicated
     * triplet in `rendering/` — [com.styleconverter.runtime.rendering
     * .ZoomApplier], chained OUTERMOST by StyleApplier.applyConfig via
     * a measure-then-scale layout node. This function survives only
     * because [applyPerformance] (itself unreferenced by the renderer)
     * calls it; chaining either would double-apply the factor.
     *
     * @param modifier Starting modifier
     * @param config ZoomConfig (the performance-local one, not rendering's)
     * @return Modified Modifier with scale
     */
    @Deprecated(
        "Paint-only zoom; use rendering.ZoomApplier.applyZoom for CSS zoom.",
        ReplaceWith("modifier")
    )
    fun applyZoom(modifier: Modifier, config: ZoomConfig): Modifier {
        if (config.isNormal) return modifier
        return modifier.scale(config.factor)
    }

    /**
     * Get the recommended compositing strategy based on containment.
     *
     * @param config ContainConfig
     * @return CompositingStrategy for graphicsLayer
     */
    fun getCompositingStrategy(config: ContainConfig): CompositingStrategy {
        return when {
            config.paint || config.isStrict || config.isContent -> CompositingStrategy.Offscreen
            config.layout -> CompositingStrategy.Auto
            else -> CompositingStrategy.Auto
        }
    }

    /**
     * Check if a graphics layer should be created.
     *
     * @param config PerformanceConfig
     * @return true if graphicsLayer is recommended
     */
    fun shouldCreateGraphicsLayer(config: PerformanceConfig): Boolean {
        return config.contain.paint ||
                config.contain.isStrict ||
                config.contain.isContent ||
                config.willChange.willTransform ||
                config.willChange.willChangeOpacity ||
                !config.zoom.isNormal
    }

    /**
     * Apply image rendering configuration.
     *
     * Note: Compose handles image rendering automatically based on
     * the Image composable and painter configuration.
     *
     * @param value ImageRenderingValue
     * @return FilterQuality recommendation
     */
    fun getFilterQuality(value: ImageRenderingValue): androidx.compose.ui.graphics.FilterQuality {
        return when (value) {
            ImageRenderingValue.AUTO -> androidx.compose.ui.graphics.FilterQuality.Medium
            ImageRenderingValue.SMOOTH -> androidx.compose.ui.graphics.FilterQuality.High
            ImageRenderingValue.HIGH_QUALITY -> androidx.compose.ui.graphics.FilterQuality.High
            ImageRenderingValue.CRISP_EDGES -> androidx.compose.ui.graphics.FilterQuality.Low
            ImageRenderingValue.PIXELATED -> androidx.compose.ui.graphics.FilterQuality.None
        }
    }

    /**
     * Helper to check if lazy layout is recommended based on containment.
     *
     * @param config ContainConfig
     * @return true if LazyColumn/LazyRow is recommended
     */
    fun shouldUseLazyLayout(config: ContainConfig): Boolean {
        // size containment hints at fixed size content that may benefit from lazy loading
        return config.size || config.isStrict || config.layout
    }

    /**
     * Result of performance analysis.
     */
    data class PerformanceHints(
        val shouldCreateLayer: Boolean,
        val compositingStrategy: CompositingStrategy,
        val shouldUseLazyLayout: Boolean,
        val filterQuality: androidx.compose.ui.graphics.FilterQuality?
    )

    /**
     * Analyze config and return performance hints.
     *
     * @param config PerformanceConfig
     * @param imageRendering Optional ImageRenderingValue
     * @return PerformanceHints with recommendations
     */
    fun analyzePerformance(
        config: PerformanceConfig,
        imageRendering: ImageRenderingValue? = null
    ): PerformanceHints {
        return PerformanceHints(
            shouldCreateLayer = shouldCreateGraphicsLayer(config),
            compositingStrategy = getCompositingStrategy(config.contain),
            shouldUseLazyLayout = shouldUseLazyLayout(config.contain),
            filterQuality = imageRendering?.let { getFilterQuality(it) }
        )
    }
}
