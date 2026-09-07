package com.styleconverter.runtime.color

// GradientInterpolation — wave 47, lane Z1 (the Android half of the
// wave-46 Y2 css-images gradient pipeline; byte-parallel twin of iOS
// StyleEngine/background/GradientInterpolation.swift).
//
// The authored <color-interpolation-method> of one gradient
// (css-images-4 §3.1 `||` clause, css-color-4 §13.2 grammar):
//   <color-interpolation-method> = in [ <rectangular-color-space>
//                                     | <polar-color-space> <hue-interpolation-method>? ]
// The converter carries it verbatim on the layer as the optional
// `interp` wire key ("in hsl longer hue", "in oklab", …) — see
// BackgroundImageProperty.kt. Before this lane the Compose extractor
// never read the key, so every polar-space gradient in the WPT
// css-images corpus rendered the plain Skia sRGB ramp (wave-46 gate:
// gradient-increasing/decreasing-hue-hsl android-ref 0.924 / 0.923;
// the powerless-hue family 0.65–0.71).
//
// Parsed here into a closed enum pair so the stop resolver can dispatch
// on it without re-validating wire text. Mirrors the iOS twin's grammar
// table exactly; spaces this runtime cannot convert yet (display-p3
// family, a98, prophoto, rec2020) fall back to [LEGACY] with a
// GradientLog breadcrumb — never silently.

/**
 * One gradient's interpolation recipe. [LEGACY] (no clause) keeps the
 * pre-wave rendering byte-identical: the resolved stops go to the Skia
 * shader untouched, which lerps them in sRGB exactly as before — every
 * committed gradient baseline is clause-less (fixtures/visual-test.json
 * Gradient_Linear/Radial/Conic/MultiStop + Edge_GradientWithRadius, all
 * enumerated in GradientStopResolverTest's dark-stage block).
 */
data class GradientInterpolation(val space: Space, val hue: HueMethod) {

    /** The interpolation colour space (css-color-4 §13.2). */
    enum class Space {
        /** No clause authored → the historical Skia sRGB shader lerp. */
        LEGACY,
        // Rectangular spaces (no hue component).
        SRGB, SRGB_LINEAR, LAB, OKLAB, XYZ_D65, XYZ_D50,
        // Polar spaces (hue arc applies, css-color-4 §13.5).
        HSL, HWB, LCH, OKLCH;

        /** True for the polar spaces — the only ones a hue method binds to. */
        val isPolar: Boolean
            get() = this == HSL || this == HWB || this == LCH || this == OKLCH
    }

    /**
     * <hue-interpolation-method> (css-color-4 §13.5). SHORTER is the
     * grammar default when a polar space carries no explicit method.
     */
    enum class HueMethod { SHORTER, LONGER, INCREASING, DECREASING }

    companion object {
        /** The clause-less default. */
        val LEGACY = GradientInterpolation(Space.LEGACY, HueMethod.SHORTER)

        /**
         * Parse the wire text. Returns [LEGACY] for absent / unparseable
         * input (the web twin drops an invalid clause the same way — an
         * invalid method would invalidate the whole declaration in a
         * browser, but the converter only emits grammar it validated, so
         * the only realistic miss here is an UNSUPPORTED space).
         */
        fun parse(raw: String?): GradientInterpolation {
            if (raw == null) return LEGACY
            // Tokenize: the converter emits single-space canonical text,
            // but split on any whitespace run for robustness.
            val t = raw.trim().lowercase().split(Regex("\\s+"))
            // Only the two legal shapes: `in <space>` / `in <polar> <method> hue`.
            if ((t.size != 2 && t.size != 4) || t[0] != "in") return LEGACY
            val space = spaceFor(t[1]) ?: run {
                // Supported by the grammar, not by this runtime's
                // conversion table yet — say so once, render legacy.
                GradientLog.once(
                    "gradient-interp-space:${t[1]}",
                    "gradient interpolation space '${t[1]}' not implemented on Android — rendering the legacy sRGB ramp"
                )
                return LEGACY
            }
            if (t.size != 4) return GradientInterpolation(space, HueMethod.SHORTER)
            // A hue tail is only grammatical on a polar space (§13.2).
            if (!space.isPolar || t[3] != "hue") return LEGACY
            val m = hueFor(t[2]) ?: return LEGACY
            return GradientInterpolation(space, m)
        }

        /**
         * Wire token → space; null for grammar-valid spaces this runtime
         * cannot convert (they surface through the logOnce above).
         */
        private fun spaceFor(s: String): Space? = when (s) {
            "srgb" -> Space.SRGB
            "srgb-linear" -> Space.SRGB_LINEAR
            "lab" -> Space.LAB
            "oklab" -> Space.OKLAB
            // css-color-4 §10.9: bare `xyz` is an alias of `xyz-d65`.
            "xyz", "xyz-d65" -> Space.XYZ_D65
            "xyz-d50" -> Space.XYZ_D50
            "hsl" -> Space.HSL
            "hwb" -> Space.HWB
            "lch" -> Space.LCH
            "oklch" -> Space.OKLCH
            else -> null
        }

        /** Wire token → hue method. */
        private fun hueFor(s: String): HueMethod? = when (s) {
            "shorter" -> HueMethod.SHORTER
            "longer" -> HueMethod.LONGER
            "increasing" -> HueMethod.INCREASING
            "decreasing" -> HueMethod.DECREASING
            else -> null
        }
    }
}

/**
 * Once-per-key breadcrumb for the gradient pipeline — the Compose stand-in
 * for iOS's PropertyTracker.logOnce. applyColors / extract run per
 * recomposition, so an unguarded log would flood logcat; "no silent
 * fallthroughs" wants loud, not spammy (same pattern as ColorApplier's
 * warnedBackgroundUrls). Synchronized set: draw happens on the UI thread,
 * tests reset from the test thread.
 */
internal object GradientLog {
    private val seen = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Log [message] the first time [key] is seen; IRLog prints on JVM too. */
    fun once(key: String, message: String) {
        if (seen.add(key)) com.styleconverter.runtime.core.ir.IRLog.warn("Gradient", message)
    }

    /** Test hook: clear the once-guard so JVM cases start known-clean. */
    internal fun resetForTest() = seen.clear()
}
