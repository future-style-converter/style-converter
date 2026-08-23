package com.styleconverter.runtime.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.effects.clip.ClipPathApplier
import com.styleconverter.runtime.effects.clip.ClipPathConfig
import com.styleconverter.runtime.effects.clip.ClipPathExtractor
import com.styleconverter.runtime.effects.filter.FilterApplier
import com.styleconverter.runtime.effects.filter.FilterConfig
import com.styleconverter.runtime.effects.filter.FilterExtractor
import com.styleconverter.runtime.effects.mask.MaskApplier
import com.styleconverter.runtime.effects.mask.MaskConfig
import com.styleconverter.runtime.effects.mask.MaskExtractor
import com.styleconverter.runtime.effects.shadow.ShadowApplier
import com.styleconverter.runtime.effects.shadow.ShadowConfig
import com.styleconverter.runtime.effects.shadow.ShadowExtractor
import com.styleconverter.runtime.effects.blend.BlendModeApplier
import com.styleconverter.runtime.effects.blend.BlendModeConfig
import com.styleconverter.runtime.effects.blend.BlendModeExtractor
import kotlinx.serialization.json.JsonElement

/**
 * Facade for all effect-related property handling.
 *
 * This provides a unified interface for extracting and applying visual effects:
 * - Filters (blur, brightness, contrast, grayscale, etc.)
 * - Backdrop filters (limited support)
 * - Box shadows (offset, blur, spread, color)
 * - Clip paths (circle, ellipse, inset, polygon, path)
 *
 * ## Usage
 * ```kotlin
 * val properties: List<Pair<String, JsonElement?>> = ...
 * val config = EffectsFacade.extractConfig(properties)
 * val modifier = EffectsFacade.apply(Modifier, config)
 * ```
 *
 * ## Shadow Usage with Corner Radius
 * ```kotlin
 * val modifier = EffectsFacade.applyWithCornerRadius(Modifier, config, cornerRadius = 8.dp)
 * ```
 *
 * ## Future Extensions
 * This facade can be extended to include:
 * - clip-path effects
 * - mask effects
 * - blend mode effects
 */
object EffectsFacade {

    /**
     * Combined configuration for all effect properties.
     */
    data class EffectsConfig(
        val filters: FilterConfig = FilterConfig(),
        val shadows: ShadowConfig = ShadowConfig(),
        val clipPath: ClipPathConfig = ClipPathConfig(),
        val mask: MaskConfig = MaskConfig(),
        val blendMode: BlendModeConfig = BlendModeConfig()
    ) {
        /** True if there are any effects to apply. */
        val hasEffects: Boolean get() = filters.hasAnyFilters || shadows.hasShadow || clipPath.hasClipPath || mask.hasMask || blendMode.hasBlendMode
    }

    /**
     * Extract all effect configurations from a list of property type/data pairs.
     *
     * @param properties List of pairs where first is the property type (e.g., "Filter")
     *                   and second is the JSON data for that property.
     * @return EffectsConfig containing configurations for all effect types.
     */
    fun extractConfig(properties: List<Pair<String, JsonElement?>>): EffectsConfig {
        return EffectsConfig(
            filters = FilterExtractor.extractFilterConfig(properties),
            shadows = ShadowExtractor.extractShadowConfig(properties),
            clipPath = ClipPathExtractor.extractClipPathConfig(properties),
            mask = MaskExtractor.extractMaskConfig(properties),
            blendMode = BlendModeExtractor.extractBlendModeConfig(properties)
        )
    }

    /**
     * Apply all effects to a modifier.
     *
     * @param modifier The modifier to apply effects to.
     * @param config The combined effects configuration.
     * @param radiusConfig The element's border-radius config, threaded to
     *   the shadow painter so the shadow perimeter follows the border-box
     *   corner shape (css-backgrounds-3 §7.1.1 — `border-radius: 50%` must
     *   cast an elliptical spread ring, not a rectangular one).
     * @return Modified modifier with effects applied.
     */
    fun apply(
        modifier: Modifier,
        config: EffectsConfig,
        radiusConfig: com.styleconverter.runtime.borders.radius.BorderRadiusConfig =
            com.styleconverter.runtime.borders.radius.BorderRadiusConfig.NONE,
        // wave-26 skeptic fix — the element's own `opacity`, consumed ONLY by
        // the backdrop path (filter-effects-2 §2 composites the filtered
        // backdrop into the element's group, so the group's opacity
        // attenuates it; ColorApplier's alpha layer sits INSIDE this chain and
        // cannot reach it). Defaulted, so nothing else in the chain changes.
        elementAlpha: Float = 1f,
        // wave-26 skeptic fix — the element's resolved margin bands, consumed
        // ONLY by the backdrop path. The whole effects step is chained OUTSIDE
        // the margin step (StyleApplier steps 3 then 4, and MarginApplier
        // emits positive margins as `absolutePadding`), so the backdrop draw
        // node is sized by the MARGIN box while filter-effects-2 §2 samples
        // and clips the BORDER box. Defaulted, so nothing else changes.
        marginInsets: com.styleconverter.runtime.spacing.MarginInsets =
            com.styleconverter.runtime.spacing.MarginInsets.NONE,
        // wave-27 fix — the element's resolved POSITION offset, consumed by
        // the two lanes that DRAW in this step's un-offset space: the
        // backdrop path and the box-shadow painter. The other half of the
        // same "step 3 is outside step 4" problem marginInsets fixes: step 4
        // chains `PositionApplier.applyPosition` → `Modifier.absoluteOffset`,
        // so an offset box paints its background (step 6) at the offset slot
        // while this step's draw nodes still sit at the un-offset one — the
        // backdrop filtered the rectangle the box used to occupy, and the
        // shadow (same defect, found next) painted its Gaussian at the
        // un-offset slot on WPT `backdrop-filter-box-shadow.html`. Defaulted,
        // so nothing else in the chain changes.
        positionOffset: androidx.compose.ui.unit.DpOffset =
            androidx.compose.ui.unit.DpOffset.Zero,
        // wave-46 (lane Y4, skeptic S2) — the CSS2 §8.3.1 collapse override
        // the element's MARGIN step receives, consumed ONLY by the clip-path
        // lane, which needs to know how much of the node is applied margin
        // band to place the css-masking-1 §7.1 reference box. Threaded as a
        // parameter for the same reason MarginApplier takes one: this step's
        // modifiers materialise INSIDE the provider that re-provides
        // `LocalCollapsedMargin` as null for descendants, so a
        // CompositionLocal read here always sees null while the margin step
        // uses the real override. Defaulted, so nothing else changes.
        collapsed: com.styleconverter.runtime.spacing.CollapsedMargin? = null,
    ): Modifier {
        var result = modifier

        // Apply blend mode first (affects layer compositing)
        result = BlendModeApplier.applyBlendMode(result, config.blendMode)

        // Apply clip path (determines visible region). The collapse
        // override rides along so the clip's reference box subtracts the
        // margin bands the margin step ACTUALLY applied (see the parameter's
        // comment above and ClipPathApplier.applyClipPath's KDoc).
        result = ClipPathApplier.applyClipPath(result, config.clipPath, collapsed)

        // Apply mask (determines visible region based on image/gradient)
        result = MaskApplier.applyMask(result, config.mask)

        // The element's own colour-matrix `filter` half — OUTERMOST of the
        // backdrop node, wave-41 correction of the wave-26 order.
        //
        // filter-effects-2 §2's Backdrop Filter algorithm composites the
        // filtered backdrop as the BOTTOM-MOST CONTENT of the element's
        // transparency group, and the group is then rendered with the
        // element's own `filter`/opacity — so `filter` MUST see the
        // backplate. Wave 26 read the resulting double-invert (an element
        // declaring both `backdrop-filter: invert(1)` and `filter:
        // invert(1)` lands back on the unfiltered colour) as a bug and moved
        // the foreground chain inside; Chrome's own ref for WPT
        // `backdrop-filter-plus-filter.html` refutes that reading — its box
        // body is dark purple = invert(white BLURRED backdrop + translucent
        // green bg), i.e. the element filter applied OVER the filtered
        // backplate (web 1.0000 vs Android 0.9168 on wave40-final, where the
        // Android box rendered nothing at all — the second, independent bug
        // this call also fixes; see applyGroupColorFilters' crop KDoc).
        // Pass A stays sound: the backplate node inside suppresses all
        // content, so this group composites nothing into the root image.
        result = FilterApplier.applyGroupColorFilters(result, config.filters, positionOffset)

        // BACKDROP filter — before the shadow, and therefore OUTER of it.
        //
        // wave-26 skeptic fix. The backdrop node suppresses this element's
        // paint during pass A by returning before `drawContent()`; anything
        // chained OUTSIDE it still paints. ShadowApplier draws via
        // `drawBehind`, so while the shadow sat outside the backdrop node the
        // element's OWN box-shadow was painted into the pass-A canvas and then
        // sampled back as part of its own backdrop — a shadow that showed up
        // blurred/inverted underneath the box that cast it. filter-effects-2
        // §2 puts the element's shadow in the ELEMENT's paint, above the
        // filtered backdrop, never in the Backdrop Root Image.
        result = FilterApplier.applyBackdropFilters(
            result, config.filters, radiusConfig, elementAlpha, marginInsets,
            positionOffset,
        )

        // Apply shadows (they render behind the content, shaped by the
        // element's border-radius — see radiusConfig KDoc above). The
        // position offset rides along because the shadow draws via
        // `drawBehind` at THIS step — outer of the layout offset — so a
        // positioned element's shadow must slide to the offset slot with
        // the box that casts it (css-backgrounds-3 §7.1 attaches the shadow
        // to the box, not to its abandoned layout slot).
        result = ShadowApplier.applyShadow(result, config.shadows, radiusConfig, positionOffset)

        // The rest of the element's own `filter` chain (blur, drop-shadow,
        // opacity) stays INNER of the shadow step, exactly where it has
        // always been, so every committed shadow capture (and the shadow
        // pins that go with them) is untouched. Only the colour-matrix half
        // moved out above — the minimal reorder the plus-filter ref demands.
        result = FilterApplier.applyForegroundFilters(result, config.filters)

        return result
    }

    /**
     * Apply all effects to a modifier, with support for rounded corner shadows.
     *
     * Use this when the element has border-radius and you want shadows
     * to match the rounded shape.
     *
     * @param modifier The modifier to apply effects to.
     * @param config The combined effects configuration.
     * @param cornerRadius The corner radius for rounded shadow rendering.
     * @return Modified modifier with effects applied.
     */
    fun applyWithCornerRadius(
        modifier: Modifier,
        config: EffectsConfig,
        cornerRadius: Dp = 0.dp
    ): Modifier {
        var result = modifier

        // Colour-matrix group outermost, for the same reason as [apply]:
        // filter-effects-2 §2 renders the element's group — filtered
        // backplate included — through the element's own `filter`.
        result = FilterApplier.applyGroupColorFilters(result, config.filters)

        // Backdrop before the shadow, for the same reason as [apply]: the
        // element's own shadow must not paint into its own pass-A backdrop.
        result = FilterApplier.applyBackdropFilters(result, config.filters)

        // Apply shadows (they render behind the content)
        result = ShadowApplier.applyShadowWithRadius(result, config.shadows, cornerRadius)

        // The blur/drop-shadow/opacity half of the filter chain, inside all
        // of the above.
        result = FilterApplier.applyForegroundFilters(result, config.filters)

        return result
    }

    /**
     * Generate a ColorFilter for image components.
     *
     * This is useful when you need to apply filter effects to images,
     * as Image composables can directly use ColorFilter.
     *
     * @param config The effects configuration.
     * @return ColorFilter if there are applicable filters, null otherwise.
     */
    fun generateColorFilter(config: EffectsConfig): ColorFilter? {
        if (!config.filters.hasFilters) return null
        return FilterApplier.generateColorFilter(config.filters.filters)
    }

    /**
     * Check if a property type is an effect-related property.
     *
     * @param type The property type string.
     * @return True if this is a filter, shadow, mask, or other effect property.
     */
    fun isEffectProperty(type: String): Boolean {
        return FilterExtractor.isFilterProperty(type) ||
                ShadowExtractor.isShadowProperty(type) ||
                ClipPathExtractor.isClipPathProperty(type) ||
                MaskExtractor.isMaskProperty(type) ||
                BlendModeExtractor.isBlendModeProperty(type)
    }
}
