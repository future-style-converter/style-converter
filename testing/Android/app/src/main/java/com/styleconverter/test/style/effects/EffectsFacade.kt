package com.styleconverter.test.style.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.effects.clip.ClipPathApplier
import com.styleconverter.test.style.effects.clip.ClipPathConfig
import com.styleconverter.test.style.effects.clip.ClipPathExtractor
import com.styleconverter.test.style.effects.filter.FilterApplier
import com.styleconverter.test.style.effects.filter.FilterConfig
import com.styleconverter.test.style.effects.filter.FilterExtractor
import com.styleconverter.test.style.effects.mask.MaskApplier
import com.styleconverter.test.style.effects.mask.MaskConfig
import com.styleconverter.test.style.effects.mask.MaskExtractor
import com.styleconverter.test.style.effects.shadow.ShadowApplier
import com.styleconverter.test.style.effects.shadow.ShadowConfig
import com.styleconverter.test.style.effects.shadow.ShadowExtractor
import com.styleconverter.test.style.effects.blend.BlendModeApplier
import com.styleconverter.test.style.effects.blend.BlendModeConfig
import com.styleconverter.test.style.effects.blend.BlendModeExtractor
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
     * @return Modified modifier with effects applied.
     */
    fun apply(modifier: Modifier, config: EffectsConfig): Modifier {
        var result = modifier

        // Apply blend mode first (affects layer compositing)
        result = BlendModeApplier.applyBlendMode(result, config.blendMode)

        // Apply clip path (determines visible region)
        result = ClipPathApplier.applyClipPath(result, config.clipPath)

        // Apply mask (determines visible region based on image/gradient)
        result = MaskApplier.applyMask(result, config.mask)

        // Apply shadows (they render behind the content)
        result = ShadowApplier.applyShadow(result, config.shadows)

        // Apply filters
        result = FilterApplier.applyFilters(result, config.filters)

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

        // Apply shadows first (they render behind the content)
        result = ShadowApplier.applyShadowWithRadius(result, config.shadows, cornerRadius)

        // Apply filters
        result = FilterApplier.applyFilters(result, config.filters)

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
