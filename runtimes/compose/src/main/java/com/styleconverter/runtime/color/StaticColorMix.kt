package com.styleconverter.runtime.color

import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.variables.CssVariableResolver
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Static `color-mix()` evaluation (css-color-5 §3) — wave-49 lane A5.
 *
 * ## Why this file exists
 *
 * The converter cannot pre-resolve a `color-mix()` whose endpoints or
 * interpolation space it will not commit to, so it ships the call verbatim:
 * `{"original":{"type":"color-mix","colorSpace":"lch","color1":"currentColor",
 * "percent1":50,"color2":"blue"}}` (verbatim wire, `tools/titan/runs/
 * wave48-final/sections/css-color/per-test-ir/
 * wpt__css-color__color-mix-currentcolor-002.json`).
 * [com.styleconverter.runtime.core.types.ValueExtractors.extractColor] only
 * evaluates the `in srgb` case with literal endpoints and returns `null` for
 * everything else, so on the wave-48 gate iOS and Android painted NOTHING for
 * six of the seven stripes of `css-color/color-mix-percents-01` (ssim 0.9074 /
 * 0.9067) and nothing at all for `color-mix-currentcolor-001/002`.
 *
 * ## What it evaluates, and with whose math
 *
 * css-color-5 §3.3 defines the result as the css-color-4 §13 INTERPOLATION of
 * the two colours at the normalised weight of the second — the identical
 * operation a gradient performs between two stops. So this object does no
 * colour math of its own: it normalises the percentages, resolves the two
 * endpoints, and hands the pair to [GradientRamp.interpolate], which already
 * implements §13.4 premultiplication, §13.5 hue-arc selection and the §13.3
 * powerless-hue carry in every space [GradientColorMath] can convert.
 * (GradientRamp / GradientColorMath / GradientInterpolation still spell those
 * rules §12.x on their own lines; css-color-4 §12 is "Comparing <color>
 * Values" and interpolation is §13 — those files are outside this wave's
 * change set, so they are reported rather than rewritten here.) The two
 * value paths can therefore never disagree about what "mix in lch" means, and
 * the iOS twin (`StyleEngine/color/StaticColorMix.swift`) reuses ITS
 * GradientRamp the same way.
 *
 * Verified against Chrome 151 and the frozen reference PNGs:
 *  - `color-mix(in lch, green 50%, blue)` → lch(37.9230 99.5929 217.8741),
 *    sRGB (0, 117, 189) — the exact pixel at (50,50) of
 *    `tools/wpt/refs/…/css-color/color-mix-currentcolor-002.png`;
 *  - `color-mix(in lch, purple 50%, plum 50%)` → sRGB (174.71, 91.81,
 *    174.15) against the value color-mix-percents-01 asserts as correct,
 *    `rgb(68.4898% 36.015% 68.3102%)` = (174.65, 91.84, 174.19).
 */
internal object StaticColorMix {

    /**
     * The `color-mix()` payload inside an IRColor wire value, or null when
     * this is not one.
     *
     * The converter nests the call under `original` (see
     * `converter/…/irmodels/ValueTypes.kt`, `ColorMix` serializer); a
     * pre-resolved colour carries `srgb` instead and never reaches here
     * because every caller tries the static decoder first.
     */
    fun payload(data: JsonElement?): JsonObject? {
        val obj = data as? JsonObject ?: return null
        val original = obj["original"] as? JsonObject ?: return null
        // `type` is the discriminator the serializer writes for every modern
        // colour form (color-mix / light-dark / relative / var).
        if ((original["type"] as? JsonPrimitive)?.contentOrNull != "color-mix") return null
        return original
    }

    /**
     * Evaluate one `color-mix()` [payload].
     *
     * @param currentColor the element's own computed `color`, substituted for
     *   a `currentColor` endpoint per css-color-4 §6.4 (the keyword resolves
     *   against the element the mix is USED on, which is why this cannot be
     *   done in the converter). Null when the chain bottomed out; a mix that
     *   needs it then stays unresolved rather than guessing an ink.
     * @return the mixed colour, or null when any part is beyond this
     *   runtime (unsupported space, unparseable endpoint, invalid
     *   percentages) — every such exit is recorded, never silent.
     */
    fun resolve(payload: JsonObject, currentColor: Color?): Color? {
        // ── 1. Interpolation space + hue method (css-color-4 §13.2/§13.5).
        // Rebuild the clause text GradientInterpolation.parse already reads
        // for gradients, so the two callers share ONE space table and one
        // set of unsupported-space breadcrumbs.
        val spaceToken = (payload["colorSpace"] as? JsonPrimitive)?.contentOrNull ?: return unsupported("no-space")
        val hueToken = (payload["hueMethod"] as? JsonPrimitive)?.contentOrNull
        val clause = if (hueToken == null) "in $spaceToken" else "in $spaceToken $hueToken hue"
        val interp = GradientInterpolation.parse(clause)
        // LEGACY means "clause not understood". Mixing in sRGB anyway would
        // paint a plausible-but-wrong colour — exactly the failure mode the
        // campaign's colour veto is too coarse to catch — so bail loudly.
        if (interp.space == GradientInterpolation.Space.LEGACY) return unsupported("space:$spaceToken")

        // ── 2. Endpoints. `currentColor` is the §6.4 substitution; anything
        // else is a CSS literal the converter copied through verbatim.
        val c1 = endpoint(payload["color1"], currentColor) ?: return unsupported("endpoint1")
        val c2 = endpoint(payload["color2"], currentColor) ?: return unsupported("endpoint2")

        // ── 3. Percentage normalisation (css-color-5 §3.2).
        val p1raw = (payload["percent1"] as? JsonPrimitive)?.doubleOrNull
        val p2raw = (payload["percent2"] as? JsonPrimitive)?.doubleOrNull
        // The grammar is <percentage [0,100]>; an out-of-range value makes
        // the whole declaration invalid (that is what WPT
        // color-mix-percents-02's `125%` / `9999%` stripes assert), so it is
        // NOT clamped into range here.
        if (p1raw != null && (p1raw < 0.0 || p1raw > 100.0)) return unsupported("percent-range")
        if (p2raw != null && (p2raw < 0.0 || p2raw > 100.0)) return unsupported("percent-range")
        // "If both are omitted they default to 50%; if one is omitted it is
        // 100% minus the other."
        val p1 = p1raw ?: p2raw?.let { 100.0 - it } ?: 50.0
        val p2 = p2raw ?: (100.0 - (p1raw ?: 50.0))
        val sum = p1 + p2
        // "If the sum is zero the function is invalid."
        if (sum <= 0.0) return unsupported("percent-zero")
        // "If the sum is less than 100%, the result's alpha is multiplied by
        // that sum" — the mix itself always runs on weights that sum to 1.
        val alphaScale = if (sum < 100.0) sum / 100.0 else 1.0

        // ── 4. The mix IS a §13 interpolation at t = the second colour's
        // normalised weight — css-color-5 §3.3 says to "interpolate a and b’s
        // colors as described in CSS Color 4 § 13".
        val t = p2 / sum
        val mixed = GradientRamp.interpolate(stop(c1), stop(c2), t, interp)
        return Color(
            mixed.r.toFloat().coerceIn(0f, 1f),
            mixed.g.toFloat().coerceIn(0f, 1f),
            mixed.b.toFloat().coerceIn(0f, 1f),
            (mixed.a * alphaScale).toFloat().coerceIn(0f, 1f),
        )
    }

    /**
     * One `color-mix()` endpoint → colour.
     *
     * The wire carries endpoints as the AUTHOR's CSS text (the `ColorMix`
     * model's `color1`/`color2` are `String`), so literals go through
     * [CssVariableResolver.parseColorValue] — the runtime's existing
     * hex / rgb() / full-named-keyword parser, which is where `plum`
     * (color-mix-percents-01) and every other CSS name already live.
     */
    private fun endpoint(node: JsonElement?, currentColor: Color?): Color? {
        val raw = (node as? JsonPrimitive)?.contentOrNull ?: return null
        // css-color-4 §6.4: the keyword is the element's own computed color.
        // ASCII case-insensitive, like every CSS keyword (css-values-4 §4.1).
        if (raw.trim().equals("currentColor", ignoreCase = true)) return currentColor
        return CssVariableResolver.parseColorValue(raw)
    }

    /** Colour → the stop shape GradientRamp interpolates (position unused). */
    private fun stop(c: Color) = GradientStopResolver.RGBAStop(
        r = c.red.toDouble(), g = c.green.toDouble(), b = c.blue.toDouble(),
        a = c.alpha.toDouble(), loc = 0f,
    )

    /**
     * Record a refusal and return null. Every non-resolution is named, so an
     * unpainted background is traceable to WHICH part of the value this
     * runtime declined — the "no silent fallthroughs" rule.
     */
    private fun unsupported(reason: String): Color? {
        PropertyTracker.markUnhandled("color-mix[$reason]")
        return null
    }
}
