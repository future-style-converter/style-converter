package com.styleconverter.test.style.appearance

// Phase 10 facade — `appearance` and related form-control-theming
// properties are parse-only on Compose (Compose Material draws its own
// theming primitives and doesn't consult CSS `appearance`).
// InteractionExtractor already consumes the `Appearance` IR property into
// its InteractionConfig for completeness (extractAppearance). This facade
// adds AppearanceVariant / ColorAdjust / ImageRenderingQuality.
//
// Parser-gap note:
//   * Appearance has a Raw catch-all; appliers must filter. We pass it
//     through unchanged.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 4 appearance-category IR properties under the `appearance`
 * owner. Parse-only on Compose.
 */
object AppearanceRegistration {

    init {
        PropertyRegistry.migrated(
            "Appearance",
            "AppearanceVariant",
            "ColorAdjust",
            "ImageRenderingQuality",
            owner = "appearance"
        )
    }
}
