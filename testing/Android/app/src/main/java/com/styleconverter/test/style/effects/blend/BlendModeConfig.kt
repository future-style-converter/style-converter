package com.styleconverter.test.style.effects.blend

// Blend-mode config holds both mix-blend-mode (a single mode applied to the whole
// element) and background-blend-mode (one mode per background layer — we only
// collect them here; the actual per-layer blending is out of scope for a pure
// modifier chain because Compose's Modifier.background stacks layers as separate
// graphicsLayers without per-pair blend targets).

import androidx.compose.ui.graphics.BlendMode

/**
 * Configuration for CSS mix-blend-mode + background-blend-mode properties.
 *
 * ## CSS Properties
 * ```css
 * .blended { mix-blend-mode: multiply; }
 * .layered { background-blend-mode: multiply, screen; }
 * ```
 *
 * ## Compose Mapping
 * - mix-blend-mode: drawWithContent + saveLayer (see BlendModeApplier)
 * - background-blend-mode: not expressible via Modifier chain; retained for
 *   diagnostic/registry completeness only.
 */
data class BlendModeConfig(
    /** Single blend mode from `mix-blend-mode`. Null = not set, SrcOver = CSS "normal". */
    val blendMode: BlendMode? = null,
    /**
     * Per-layer blend modes from `background-blend-mode`. Parallel to the
     * BackgroundImage layers list — index N here describes how layer N blends
     * against the composite of layers 0..N-1. Empty list = unset.
     */
    val backgroundBlendModes: List<BlendMode> = emptyList()
) {
    /** True when mix-blend-mode is set to something other than SrcOver (normal). */
    val hasBlendMode: Boolean get() = blendMode != null && blendMode != BlendMode.SrcOver

    /** True when there is at least one non-default background-blend-mode. */
    val hasBackgroundBlendMode: Boolean get() = backgroundBlendModes.any { it != BlendMode.SrcOver }
}

/**
 * CSS mix-blend-mode string values mapped to Compose BlendMode.
 * Covers all 18 values the IR parser emits (see CLAUDE.md — "18 values").
 */
object BlendModeMapping {

    /**
     * Parse an IR blend-mode token (case-insensitive; either hyphen or underscore)
     * to a Compose BlendMode. Returns null when unknown — callers treat that
     * as "leave default", which on Compose is SrcOver.
     */
    fun fromCssValue(value: String): BlendMode? {
        // Normalize: uppercase, collapse hyphens to underscores — the IR emits
        // either "PLUS_LIGHTER" or "plus-lighter" depending on producer.
        return when (value.uppercase().replace("-", "_")) {
            "NORMAL" -> BlendMode.SrcOver
            "MULTIPLY" -> BlendMode.Multiply
            "SCREEN" -> BlendMode.Screen
            "OVERLAY" -> BlendMode.Overlay
            "DARKEN" -> BlendMode.Darken
            "LIGHTEN" -> BlendMode.Lighten
            "COLOR_DODGE", "COLORDODGE" -> BlendMode.ColorDodge
            "COLOR_BURN", "COLORBURN" -> BlendMode.ColorBurn
            "HARD_LIGHT", "HARDLIGHT" -> BlendMode.Hardlight
            "SOFT_LIGHT", "SOFTLIGHT" -> BlendMode.Softlight
            "DIFFERENCE" -> BlendMode.Difference
            "EXCLUSION" -> BlendMode.Exclusion
            "HUE" -> BlendMode.Hue
            "SATURATION" -> BlendMode.Saturation
            "COLOR" -> BlendMode.Color
            "LUMINOSITY" -> BlendMode.Luminosity
            // CSS plus-lighter maps to Skia's PLUS (additive). plus-darker has
            // no direct Compose equivalent; we approximate with Multiply.
            "PLUS_LIGHTER", "PLUSLIGHTER" -> BlendMode.Plus
            "PLUS_DARKER", "PLUSDARKER" -> BlendMode.Multiply
            else -> null
        }
    }
}
