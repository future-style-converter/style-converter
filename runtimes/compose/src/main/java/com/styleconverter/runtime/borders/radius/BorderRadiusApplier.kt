package com.styleconverter.runtime.borders.radius

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.borders.sides.AllBordersConfig
import com.styleconverter.runtime.core.types.ValueExtractors.LineStyle

/**
 * Applies CSS border-radius to a Compose modifier.
 *
 * Two code paths, chosen by [BorderRadiusConfig.isCircular]:
 *   - Circular (x == y per corner): uses the built-in [RoundedCornerShape]
 *     which is cheaper and hits Compose's fast-path corner rasterizer.
 *   - Elliptical (x != y on any corner): uses a custom [Shape] that builds
 *     a [RoundRect] with per-corner [CornerRadius] (a, b) values. This is
 *     what CSS `border-radius: 40px 20px` actually means — and why the
 *     previous (Dp-per-corner) implementation silently collapsed elliptical
 *     pairs to just the horizontal radius.
 *
 * And two APPLICATION modes, chosen by [selfPaintsWithoutClip] at the
 * StyleApplier call site (wave 42):
 *   - [applyRadius] — the legacy `Modifier.clip`, which clips EVERYTHING
 *     inner to it, child composables included. Correct only when the
 *     element actually establishes a clip (overflow != visible), and kept
 *     as the fallback for paint the self-paint mode cannot shape yet.
 *   - [applySelfPaint] — shape the element's OWN paint (background fill +
 *     uniform border band) with the corner shape and clip NOTHING.
 *     css-backgrounds-3 §4.3: "the border radii … cause the background and
 *     border to be curved" — the box's DESCENDANTS are only clipped when
 *     `overflow` says so (css-overflow-3 §3). The old always-clip path cut
 *     children away wholesale: WPT `filter-effects/backdrop-filter-
 *     clip-rect.html`'s `.navbar` (border-radius + 2px blue border) holds
 *     two red-bordered abspos `.menu` boxes that overflow it — wave-41 T5
 *     counted 1552 red pixels in the Chromium ref and in the iOS capture,
 *     and ZERO on Android (android-ref 0.8651).
 */
object BorderRadiusApplier {

    /**
     * Apply border radius by clipping the modifier to the corner shape.
     *
     * Clip is used here because this path serves the cases where the CSS
     * paint model really does bound the visible region at the rounded
     * border box: an `overflow != visible` element (css-overflow-3 §3
     * clips at the padding box, whose corners are rounded per
     * css-backgrounds-3 §4.2), and the not-yet-self-paintable combos
     * (see [selfPaintsWithoutClip]) where clipping background layers to
     * the corner shape matters more than child overflow.
     */
    fun applyRadius(modifier: Modifier, config: BorderRadiusConfig): Modifier {
        // Zero-radius fast path: no Shape allocation, no clip pass.
        if (!config.hasRadius) return modifier
        // Shared corner-shape choice (see shapeFor) wrapped in the
        // children-clipping graphicsLayer clip.
        return modifier.clip(shapeFor(config))
    }

    /**
     * The corner [Shape] for [config] — the ONE shape both application
     * modes use, so the clip path and the self-paint path can never
     * disagree about the corner geometry.
     */
    internal fun shapeFor(config: BorderRadiusConfig): Shape {
        // Circular fast path: the built-in RoundedCornerShape is the cheapest
        // and most-tested Shape; use it whenever every corner is x==y AND no
        // corner carries a paint-time percentage axis (fractions need the
        // custom Shape below to resolve against the laid-out size).
        if (config.isCircular && !config.hasFraction) {
            return RoundedCornerShape(
                topStart = config.topStart.first,
                topEnd = config.topEnd.first,
                bottomEnd = config.bottomEnd.first,
                bottomStart = config.bottomStart.first
            )
        }
        // Elliptical / percentage path: hand-built Shape that emits a
        // RoundRect with independent x/y radii per corner — this is what
        // CSS "40px 20px" and "50%" actually specify. Percentage axes are
        // resolved here against the box's width (x) / height (y) per
        // css-backgrounds-3 §4.4.
        return EllipticalCornerShape(config)
    }

    /**
     * Should this element take the non-clipping self-paint mode?
     *
     * Pure truth table (JVM-pinned in BorderRadiusSelfPaintTest). True
     * exactly when EVERY radius-shaped paint the element owns can be
     * produced by [applySelfPaint], so dropping the clip loses nothing:
     *  - a radius exists at all (otherwise neither mode installs);
     *  - the element does NOT establish an overflow clip — with
     *    `overflow: hidden/clip/scroll/auto` the children really are
     *    clipped at the rounded box (css-overflow-3 §3), which is exactly
     *    what the legacy clip renders;
     *  - no background-image layers and no background-clip insets — those
     *    paint in ColorApplier as RECTANGLES and today only look rounded
     *    because the clip cuts their corners; self-paint would un-round
     *    them. Kept on the legacy path (a deliberate, documented residual:
     *    children under gradient+radius stay clipped until the layer
     *    painter learns shapes);
     *  - the border is absent or uniform-solid — the one band
     *    [applySelfPaint] can draw exactly (Modifier.border follows the
     *    Shape). Mixed per-side widths/styles keep the legacy clip whose
     *    straight strokes + corner cut are today's committed rendering;
     *  - the element carries NO partial opacity — ColorApplier applies
     *    `opacity` as its OUTERMOST link (`Modifier.alpha`, its step 1),
     *    so the legacy chain attenuates the background it paints at its
     *    step 2, INSIDE the alpha layer. Self-paint moves the fill into
     *    THIS applier, chained OUTER of ColorApplier — under opacity < 1
     *    the rounded fill would escape the alpha layer and paint at full
     *    strength (css-color-4 §3.3 groups the element's whole paint).
     *    Kept legacy: the child cut is the smaller committed error there.
     */
    fun selfPaintsWithoutClip(
        radius: BorderRadiusConfig,
        sides: AllBordersConfig,
        overflowClips: Boolean,
        hasBackgroundLayers: Boolean,
        hasClipInsets: Boolean,
        hasPartialOpacity: Boolean,
    ): Boolean {
        // No radius → nothing for either mode to do.
        if (!radius.hasRadius) return false
        // Overflow clip requested → the children-clipping mode IS the spec.
        if (overflowClips) return false
        // Rectangular background layers would lose their rounding.
        if (hasBackgroundLayers || hasClipInsets) return false
        // Partial opacity → the fill must stay INSIDE ColorApplier's alpha
        // layer (see the truth-table bullet above); legacy owns that order.
        if (hasPartialOpacity) return false
        // Border must be self-paintable: none, or the uniform-solid band.
        return !sides.hasBorders || isUniformSolid(sides)
    }

    /**
     * The uniform-solid predicate, shared with the truth table above: all
     * four sides identical AND the DECLARED style is SOLID. `hasBorder`
     * already requires a declared visible style (BorderSideConfig.kt —
     * CSS 2.1 §8.5.3: an absent style is `none`, used width 0), so the
     * comparison is against the keyword itself; BorderSideApplier's fast
     * path and paintSide gate the same way. (retro R4 / A11#5: the old
     * note "absent style defaults to solid … like BorderSideApplier's fast
     * path" described a default neither draw path takes — the elvis was
     * dead code that misled the iOS twin into painting width-only borders
     * as solid bands.)
     */
    internal fun isUniformSolid(sides: AllBordersConfig): Boolean =
        sides.isUniform && sides.top.hasBorder && sides.top.style == LineStyle.SOLID

    /**
     * Self-paint mode: round the element's OWN paint, clip nothing.
     *
     * Two platform primitives, both Shape-aware by construction:
     *  - `Modifier.background(color, shape)` fills ONLY the shape's
     *    outline (androidx Background draws the shape's Outline path, it
     *    never clips content) — the rounded background fill of
     *    css-backgrounds-3 §4.3;
     *  - `Modifier.border(width, color, shape)` strokes the border band
     *    BETWEEN the shape's outline and its width-inset inner outline
     *    (androidx BorderModifierNode) — the curved border band of §4.3,
     *    with the inner corner curvature (outer radius minus width) the
     *    straight-stroke painter never had.
     *
     * Chain order: background FIRST (outer), border second — Compose draws
     * outer modifiers first, so the band paints on top of the fill's edge
     * exactly like the legacy chain painted strokes over the background
     * (css-backgrounds-3 §2's painting order: background, then border).
     * Children are untouched: neither primitive clips, so an overflowing
     * child paints in full (the clip-rect red menus).
     */
    fun applySelfPaint(
        modifier: Modifier,
        radius: BorderRadiusConfig,
        sides: AllBordersConfig,
        backgroundColor: Color?,
    ): Modifier {
        // One shared Shape for both paints — the same geometry the clip
        // mode would have used (see shapeFor).
        val shape = shapeFor(radius)
        var result = modifier
        // Rounded background fill, only when the element declares one
        // (ColorApplier's solid-color paint is suppressed by the caller in
        // this mode, so this is the single owner of the fill).
        if (backgroundColor != null) {
            result = result.background(backgroundColor, shape)
        }
        // Rounded uniform border band, only when the gate proved the border
        // is the uniform-solid kind this primitive renders exactly.
        if (sides.hasBorders && isUniformSolid(sides)) {
            // Same defaults as BorderSideApplier's uniform fast path
            // (width 1px, color black) so mode choice never changes the
            // resolved band.
            result = result.border(
                width = sides.top.width ?: 1.dp,
                color = sides.top.color ?: Color.Black,
                shape = shape
            )
        }
        return result
    }
}

/**
 * Custom [Shape] that produces elliptical per-corner radii by building a
 * [RoundRect] with eight independent CornerRadius components.
 *
 * Compose's [RoundedCornerShape] only accepts a single Dp per corner and
 * uses it for both axes — insufficient for CSS semantics. This Shape
 * mirrors the standard Compose corner-shape API (including RTL handling
 * via [LayoutDirection]) so it drops into `Modifier.clip` transparently.
 */
private class EllipticalCornerShape(
    private val config: BorderRadiusConfig
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // Resolve logical start/end to physical left/right at paint time —
        // this matches how RoundedCornerShape handles RTL, so our custom
        // shape behaves consistently with the rest of the style engine.
        val ltr = layoutDirection == LayoutDirection.Ltr

        // Convert each corner to pixel CornerRadius(x, y). A percentage
        // axis (carried as a 0..1 fraction) resolves against the box's
        // WIDTH for x and HEIGHT for y — css-backgrounds-3 §4.4. That's
        // why `border-radius: 50%` yields a full ellipse on a non-square
        // box (rx = w/2, ry = h/2), matching Chrome. Fixed axes convert
        // Dp→px via the Density Compose hands us here.
        fun cr(
            pair: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>,
            fraction: Pair<Float?, Float?>
        ) = with(density) {
            CornerRadius(
                fraction.first?.times(size.width) ?: pair.first.toPx(),
                fraction.second?.times(size.height) ?: pair.second.toPx()
            )
        }

        // Map logical corners (start/end) to physical corners (left/right)
        // based on layout direction. In LTR: start=left, end=right.
        val cTopStart = cr(config.topStart, config.topStartFraction)
        val cTopEnd = cr(config.topEnd, config.topEndFraction)
        val cBottomEnd = cr(config.bottomEnd, config.bottomEndFraction)
        val cBottomStart = cr(config.bottomStart, config.bottomStartFraction)
        val topLeft = if (ltr) cTopStart else cTopEnd
        val topRight = if (ltr) cTopEnd else cTopStart
        val bottomRight = if (ltr) cBottomEnd else cBottomStart
        val bottomLeft = if (ltr) cBottomStart else cBottomEnd

        // RoundRect with per-corner CornerRadius(x, y) — this is the only
        // geometry type in Compose that represents true elliptical corners.
        val rr = RoundRect(
            rect = Rect(0f, 0f, size.width, size.height),
            topLeft = topLeft,
            topRight = topRight,
            bottomRight = bottomRight,
            bottomLeft = bottomLeft
        )
        // Wrap in a Path so the Outline accepts a non-convex rounded rect
        // (Outline.Rounded requires a convex RoundRect, which ours always
        // is, but Path works for any clip target and is just as fast after
        // the first rasterization).
        return Outline.Generic(Path().apply { addRoundRect(rr) })
    }
}
