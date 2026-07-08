package app.parsing.css.properties.longhands.color

import app.irmodels.IRProperty
import app.irmodels.properties.color.MixBlendModeProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `mix-blend-mode` property.
 *
 * Syntax: normal | multiply | screen | overlay | darken | lighten | color-dodge | color-burn |
 *         hard-light | soft-light | difference | exclusion | hue | saturation | color | luminosity |
 *         plus-darker | plus-lighter
 */
object MixBlendModePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val mode = when (trimmed) {
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
            "plus-darker" -> MixBlendModeProperty.BlendMode.PLUS_DARKER
            "plus-lighter" -> MixBlendModeProperty.BlendMode.PLUS_LIGHTER
            else -> return null
        }

        return MixBlendModeProperty(mode)
    }
}
