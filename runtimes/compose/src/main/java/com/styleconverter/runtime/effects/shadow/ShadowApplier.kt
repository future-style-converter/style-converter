package com.styleconverter.runtime.effects.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
// The resolved position offset rides in as a value (DpOffset) — see the
// positionOffset param KDoc on applyFullShadow for why the shadow painters,
// chained OUTER of the layout offset, have to translate by it.
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
// The resolved margin bands ride in as a value too (retro round-2 F1) — see
// the marginInsets param KDoc on applyFullShadow.
import com.styleconverter.runtime.spacing.MarginInsets

/**
 * Applies box shadow effects to Compose modifiers.
 *
 * ## Implementation Strategy
 *
 * ### All shadows are CSS-style Gaussian glows
 * Every outset shadow goes through `drawBehind` + `BlurMaskFilter`. There
 * is deliberately NO `Modifier.shadow(elevation)` fast path: elevation is
 * a physically-modeled z-light shadow (two fixed-alpha ambient/spot
 * layers whose geometry depends on the device's simulated light source),
 * NOT the colored Gaussian glow css-backgrounds-3 §6.1 specifies — a
 * `box-shadow: 0 0 40px red` routed through elevation rendered as a faint
 * grey-ish rim instead of a wide red halo, which is exactly the class of
 * divergence the campaign diagnosed. The custom draw path handles color,
 * blur, and shape correctly for every parameter combination.
 *
 * ## Limitations
 * - **Spread radius**: Implemented by expanding/contracting the drawn rect
 * - **Inset shadows**: Basic support; complex insets may not render correctly
 * - **Multiple shadows**: All shadows are drawn; overlapping layers on a
 *   translucent element composite per layer, not flattened-then-attenuated
 *   (see ShadowGeometry.attenuate)
 * - **Performance**: Custom drawing is less optimized than native elevation
 *
 * Outset shadows are clipped OUT of the border box and attenuated by the
 * element's `opacity` (retro R6 — see OutsetShadowPainter.paint); both
 * painters strip the element's margin bands off their draw node first
 * (retro round-2 F1 — see ShadowGeometry's class KDoc).
 *
 * ## Platform Comparison
 * | Feature | CSS | Compose (native) | Compose (custom) |
 * |---------|-----|------------------|------------------|
 * | Basic shadow | Yes | Yes (elevation) | Yes |
 * | Offset X/Y | Yes | No | Yes |
 * | Blur radius | Yes | Yes (via elevation) | Yes |
 * | Spread radius | Yes | No | Yes (approximate) |
 * | Inset | Yes | No | Partial |
 * | Custom color | Yes | Limited | Yes |
 * | Multiple shadows | Yes | No | Yes |
 */
object ShadowApplier {

    /**
     * Apply box shadow to a modifier.
     *
     * @param modifier The base modifier to apply shadows to.
     * @param config The shadow configuration.
     * @param radiusConfig The element's border-radius config. css-backgrounds-3
     *   §6.1.1: the shadow's perimeter takes the SAME corner shape as the
     *   border box, so a `border-radius: 50%` element casts an ELLIPTICAL
     *   spread ring. Previously the ring was always a (near-)rectangular
     *   round-rect, so Borders_Decorated's blue 3px ring filled the whole
     *   rectangular box behind the orange ellipse on Android while web/iOS
     *   drew a ring hugging the ellipse (Android-web 0.7685).
     * @param elementAlpha The element's own `opacity` (retro R6, A11#2) —
     *   css-color-4 §3.3 applies it "to the element as a whole", shadow
     *   included; see ShadowGeometry.attenuate. Default 1 = fully opaque, so
     *   every existing call site keeps its behaviour.
     * @param marginInsets The element's resolved margin bands (retro round-2
     *   F1) — see applyFullShadow's KDoc. Default NONE, so every existing
     *   call site keeps its behaviour.
     * @return Modified modifier with shadows applied.
     */
    fun applyShadow(
        modifier: Modifier,
        config: ShadowConfig,
        radiusConfig: com.styleconverter.runtime.borders.radius.BorderRadiusConfig =
            com.styleconverter.runtime.borders.radius.BorderRadiusConfig.NONE,
        // Threaded through from EffectsFacade — see applyFullShadow's KDoc.
        positionOffset: DpOffset = DpOffset.Zero,
        // The element's `opacity`, threaded from EffectsFacade.apply.
        elementAlpha: Float = 1f,
        // The element's margin bands, threaded from EffectsFacade.apply.
        marginInsets: MarginInsets = MarginInsets.NONE,
    ): Modifier {
        return applyFullShadow(modifier, config, 0.dp, radiusConfig, positionOffset, elementAlpha, marginInsets)
    }

    /**
     * Apply box shadow with full support for spread, inset, and multiple shadows.
     *
     * @param modifier The base modifier to apply shadows to.
     * @param config The shadow configuration.
     * @param cornerRadius Legacy single-value corner radius (used when no
     *   [radiusConfig] is supplied by the caller).
     * @param radiusConfig Per-corner border-radius (Dp + paint-time
     *   percentage fractions) driving the shadow perimeter shape.
     * @param positionOffset The element's resolved position offset —
     *   `PositionApplier.resolvedOffset`, literally the value form of the
     *   `absoluteOffset` StyleApplier's step 4 chains INNER of this draw
     *   node. The shadow paints in the step-3 node's space, i.e. at the
     *   element's UN-offset layout slot, while the element's own
     *   background/borders (steps 5–6) paint at the offset one — so every
     *   shadow layer must translate by this value or a positioned element
     *   casts its shadow at the slot it never visually occupied (the same
     *   wave-27 geometry correction the backdrop lane already carries;
     *   measured on WPT `backdrop-filter-box-shadow.html`, where the grey
     *   shadow of a `position:absolute; top:125px; left:75px` box painted
     *   up in the explanatory-text band). Defaulted to zero, so every
     *   static-element caller — the overwhelming majority — is untouched.
     * @param marginInsets The element's resolved POSITIVE margin bands —
     *   `MarginApplier.resolvedInsets`, literally the value form of the
     *   `absolutePadding` step 4 chains INNER of this draw node (retro
     *   round-2 F1, skeptic S3 must-fix). The OTHER half of the same "step 3
     *   is outer of step 4" problem the position offset fixes: the draw
     *   node's `size` is the MARGIN box (CSS2 §8.1), so R6's §6.1.1 knockout
     *   knocked out the whole margin box and the perimeter sat one band too
     *   far out on every margined element — R6's own fixture
     *   BSK_Op55_RingDominant (`margin: 24px`, 20px spread on a 10px box)
     *   predicted a 98×98 ring with a 24px ground gap instead of its 50×50
     *   ring, and the wave49-final css-view-transitions fractional-box-with-
     *   shadow-new/-old cells (margin 15px, spread 7) carry the same shape.
     *   Both painters subtract the bands via ShadowGeometry.borderBoxRect /
     *   outsetShadowRect, exactly as the backdrop lane does. Defaulted to
     *   NONE, so every margin-less caller is byte-identical.
     * @return Modified modifier with shadows applied.
     */
    fun applyFullShadow(
        modifier: Modifier,
        config: ShadowConfig,
        cornerRadius: Dp = 0.dp,
        radiusConfig: com.styleconverter.runtime.borders.radius.BorderRadiusConfig =
            com.styleconverter.runtime.borders.radius.BorderRadiusConfig.NONE,
        positionOffset: DpOffset = DpOffset.Zero,
        // The element's `opacity` — consumed by the outset painter only (an
        // inset shadow paints INSIDE the box, under the step-6 group already).
        elementAlpha: Float = 1f,
        // The element's margin bands — consumed by BOTH painters (both draw
        // nodes are sized by the margin box).
        marginInsets: MarginInsets = MarginInsets.NONE,
    ): Modifier {
        if (!config.hasShadow) return modifier

        // Separate inset and outset shadows
        val outsetShadows = config.shadows.filter { !it.inset }
        val insetShadows = config.shadows.filter { it.inset }

        var resultModifier = modifier

        // Apply outset shadows first (drawn behind content)
        if (outsetShadows.isNotEmpty()) {
            resultModifier = OutsetShadowPainter.paint(
                resultModifier, outsetShadows, cornerRadius, radiusConfig, positionOffset, elementAlpha,
                marginInsets,
            )
        }

        // Apply inset shadows (drawn over content, clipped to bounds)
        if (insetShadows.isNotEmpty()) {
            resultModifier = InsetShadowPainter.paint(
                resultModifier, insetShadows, cornerRadius, positionOffset, marginInsets,
            )
        }

        return resultModifier
    }

    /**
     * Apply shadow with rounded corners.
     *
     * @param modifier The base modifier.
     * @param config The shadow configuration.
     * @param cornerRadius The corner radius for rounded rect shadows.
     * @return Modified modifier with rounded shadows applied.
     */
    fun applyShadowWithRadius(
        modifier: Modifier,
        config: ShadowConfig,
        cornerRadius: Dp = 0.dp
    ): Modifier {
        if (!config.hasShadow) return modifier

        // Use full shadow implementation for complete support
        return applyFullShadow(modifier, config, cornerRadius)
    }

    // The two painters (outset: knockout + group alpha + BlurMaskFilter
    // layers; inset: clip + negative-space hole) live in OutsetShadowPainter
    // (carved out in retro R6) and InsetShadowPainter (carved out in retro
    // round-2 F1), both under the file-size rule; applyFullShadow calls them
    // with the same arguments the private applyOutsetShadows /
    // applyInsetShadows used to take, plus the margin bands.

    /**
     * Convert a CSS box-shadow blur radius (px) into the radius argument
     * Skia's [android.graphics.BlurMaskFilter] expects.
     *
     * Derivation:
     * - css-backgrounds-3 §6.1.2: a blur radius `r` means a Gaussian blur
     *   whose standard deviation is `r / 2` — this is what Chromium (and
     *   the web runtime under it) implements, so σ = r/2 is our parity
     *   target.
     * - Skia's BlurMaskFilter maps its radius argument to a sigma via
     *   `sigma ≈ 0.57735 · radius + 0.5` (skia/src/core/SkBlurMask.cpp,
     *   `SkBlurMask::ConvertRadiusToSigma`).
     * - Equate the two and solve for radius:
     *   `radiusForSkia = max(0, (r/2 − 0.5) / 0.57735)`.
     *
     * Passing the raw CSS radius straight through (the old behavior) made
     * the effective sigma ≈ 0.577·r + 0.5 instead of r/2 — roughly 2.3×
     * too soft/wide, a visible cross-platform divergence at 40px blurs.
     * Result 0 (r ≤ 1px) means "skip the filter": BlurMaskFilter throws
     * on non-positive radii and a sub-pixel sigma reads as a crisp edge.
     */
    internal fun blurMaskRadius(cssBlurPx: Float): Float =
        (((cssBlurPx / 2f) - 0.5f) / 0.57735f).coerceAtLeast(0f)

}
