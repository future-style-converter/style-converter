package app.parsing.css.properties.longhands.background

import app.irmodels.IRProperty
import app.irmodels.properties.color.BackgroundBlendModeProperty
import app.irmodels.properties.color.MixBlendModeProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `background-blend-mode` property.
 *
 * Syntax: <blend-mode> [, <blend-mode>]*
 * blend-mode: normal | multiply | screen | overlay | darken | lighten | color-dodge | color-burn |
 *             hard-light | soft-light | difference | exclusion | hue | saturation | color | luminosity
 */
object BackgroundBlendModePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.split(",").map { it.trim().lowercase() }
        val modes = parts.mapNotNull { parseSingleMode(it) }

        if (modes.isEmpty()) return null

        return BackgroundBlendModeProperty(modes)
    }

    private fun parseSingleMode(value: String): MixBlendModeProperty.BlendMode? {
        return when (value) {
            "normal" -> MixBlendModeProperty.BlendMode.NORMAL
            "multiply" -> MixBlendModeProperty.BlendMode.MULTIPLY
            "screen" -> MixBlendModeProperty.BlendMode.SCREEN
            "overlay" -> MixBlendModeProperty.BlendMode.OVERLAY
            "darken" -> MixBlendModeProperty.BlendMode.DARKEN
            "lighten" -> MixBlendModeProperty.BlendMode.LIGHTEN
            "color-dodge" -> MixBlendModeProperty.BlendMode.COLOR_DODGE
            "color-burn" -> MixBlendModeProperty.BlendMode.COLOR_BURN
            "hard-light" -> MixBlendModeProperty.BlendMode.HARD_LIGHT
            "soft-light" -> MixBlendModeProperty.BlendMode.SOFT_LIGHT
            "difference" -> MixBlendModeProperty.BlendMode.DIFFERENCE
            "exclusion" -> MixBlendModeProperty.BlendMode.EXCLUSION
            "hue" -> MixBlendModeProperty.BlendMode.HUE
            "saturation" -> MixBlendModeProperty.BlendMode.SATURATION
            "color" -> MixBlendModeProperty.BlendMode.COLOR
            "luminosity" -> MixBlendModeProperty.BlendMode.LUMINOSITY
            else -> null
        }
    }
}
