package com.styleconverter.runtime.sizing

// Wave 55 (lane L2, unit U2) — PercentContentBoxBand: the in-flow PERCENTAGE
// size of a content-box box gets its padding + border band at LAYOUT time
// (split out of SizingApplier, which sits far past the house ceiling).
//
// ## The spec
// css-ui-3 §3.1 (`box-sizing: content-box`, the UA initial value the WPT
// capture emulates — SizingApplier.effectiveBoxSizing): width/height size
// the CONTENT box; padding and border lie OUTSIDE it. CSS 2.1 §10.2 / §10.5:
// a percentage resolves against the containing block. So the border box is
// `fraction × containing-block size + bands`, and a box larger than its
// container still lays out at that size and overflows (css-overflow-3 §3);
// the container's own clip, if any, hides the excess.
//
// ## The measured defect (wave-55 brief cbb-android.md §2)
// SizingApplier.inflateForContentBox passes a PERCENT size through untouched
// (a px band cannot be added to a fraction statically), and
// fillMaxWidth/Height(fraction) then makes the percentage the BORDER box —
// the padding chained innermost (StyleApplier.applyConfig step 8) eats into
// it. WPT filter-effects/backdrop-filter-nested-border-radius-clip-3's
// #middle (`width/height: 100%; padding: 10px` inside #outer's 200×100
// content box) drew as a 200×100 border box (content 180×80) where Chromium
// draws 220×120 (content 200×100): the android green stopped 20 px short on
// both axes, (36,36,215,115) against the ref's (36,36,235,135), measured on
// the wave54-probe capture with the CBB-android channel fix (unit U1) applied.
//
// ## The mechanism
// One layout step per axis: FillNode's own `round(max × fraction)` PLUS the
// band, then ExactWidthOverflow's house overflow split — the child measures
// at the target, the parent is told the incoming max (it must not grow, as
// in the browser), and the placeable is START-anchored so the excess spills
// toward the inline/block END (never centred like Compose's requiredWidth).
// An UNBOUNDED axis is identity, FillNode parity (fillMax* is a no-op there).
//
// ## Radius (census, cbb-android.census.out.txt B, re-run by the lane)
// 2 of the corpus's 122 percentage axes: #middle (0-172) W and H of
// nested-border-radius-clip-3. WPT capture only; the dark stage keeps the
// frozen fill and breadcrumbs (no committed fixture declares content-box +
// a % size + a band, census C).
// KNOWN LIMIT (named; 0 corpus carriers — the P-U2-6 census resolves every box
// with blockLevelWidthAuto on): DynamicValueResolver.invalidWidthFallback stands
// a guaranteed-invalid block `width` in as the wire's 100% shape, i.e. `auto`;
// with a band this lane would draw it 100% + band where CSS auto fits the block.

// PropertyTracker: the two named breadcrumbs (no silent fallthrough).
import com.styleconverter.runtime.PropertyTracker
// The size shapes the lane routes on (Relative PERCENT only).
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
// The custom measure hook — the same LayoutModifier shape CalcSizeMinModifier
// uses, so a JVM chain test can find this node and read its inputs.
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
// Constraints.Infinity marks the unbounded (identity) axis.
import androidx.compose.ui.unit.Constraints
// CSS px == Dp in this runtime (SizingConfig's bands are CSS px).
import androidx.compose.ui.unit.dp
// FillNode's rounding (fastRoundToInt ≡ roundToInt on finite floats).
import kotlin.math.roundToInt

/** The pure half: JVM-pinned in PercentContentBoxBandTest. */
internal object PercentContentBoxBand {

    /** A dark-stage box that DECLARES content-box + a % size + a band keeps the frozen fill. */
    const val DARK_STAGE_BREADCRUMB = "Sizing[dark-stage-percent-content-box-band]"

    /** A % axis the min/max lane (PercentSizeClamp) owns does not get the band yet. */
    const val CLAMP_BREADCRUMB = "Sizing[percent-clamp-content-box-band]"

    /**
     * Does this axis take the fill-plus-band lane? True iff ALL hold:
     * WPT capture; the effective box-sizing is content-box; [raw] is a
     * `Relative(_, PERCENT)` size; [bandPx] (SizingExtractor's
     * contentBoxInflateX/Y) is non-zero; PercentSizeClamp does not own the
     * axis ([clampOwnsAxis] = SizingClamps.percentClampSpec != null).
     */
    fun owns(raw: LengthValue?, cfg: SizingConfig, bandPx: Float, clampOwnsAxis: Boolean): Boolean {
        // Only a percentage carries the fraction this lane adds a band to.
        if (raw !is LengthValue.Relative || raw.unit != LengthUnit.PERCENT) return false
        // Only an effective content-box puts the bands outside the percentage
        // (a declared border-box, and the dark stage's unset null, keep the fill).
        if (cfg.boxSizing != BoxSizingKeyword.CONTENT_BOX) return false
        // A zero band changes nothing: the fill is already the border box.
        if (bandPx == 0f) return false
        // Dark stage (an explicit content-box): frozen fill, named not fixed.
        if (!cfg.wptCaptureMode) return breadcrumb(DARK_STAGE_BREADCRUMB)
        // The min/max lane owns the axis through one clamp: named limitation.
        if (clampOwnsAxis) return breadcrumb(CLAMP_BREADCRUMB)
        // Every condition holds: the band is added at layout time.
        return true
    }

    /** Record [key] once per call site visit and decline the axis. */
    private fun breadcrumb(key: String): Boolean {
        // PropertyTracker is the module's breadcrumb channel (cf. ContainingBlockBands).
        PropertyTracker.markUnhandled(key)
        // Declining keeps the pre-existing fill / clamp chain on that axis.
        return false
    }

    /**
     * The fraction exactly as SizingApplier's fill branch computes it —
     * `(value / 100).coerceIn(0, 1)` — so only the band is new. (A % above
     * 100 stays clamped to the full fill, the pre-existing fillMax* limit.)
     */
    fun fraction(raw: LengthValue?): Float =
        // A non-percent input never reaches here (owns gates it); 0 is inert.
        (((raw as? LengthValue.Relative)?.value?.toFloat() ?: 0f) / 100f).coerceIn(0f, 1f)

    /** The border-box target: `round(max × fraction + band)` (css-ui-3 §3.1 + CSS 2.1 §10.2). */
    fun targetPx(fraction: Float, maxPx: Int, bandPx: Float): Int =
        // FillNode's own product, the band added before the one rounding.
        (maxPx * fraction + bandPx).roundToInt()

    /**
     * (child, reported) for one axis, or null when the axis is UNBOUNDED
     * (identity — FillNode parity). The split is ExactWidthOverflow's: an
     * oversized target is measured as is and reported as the incoming max.
     */
    fun measureSpec(fraction: Float, bandPx: Float, incomingMin: Int, incomingMax: Int): Pair<Int, Int>? {
        // No containing-block size to resolve against: leave the axis alone.
        if (incomingMax == Constraints.Infinity) return null
        // The house overflow split (fitting: tight coercion; overflow: measure big, report max).
        return ExactWidthOverflow.measureSpec(targetPx(fraction, incomingMax, bandPx), incomingMin, incomingMax)
    }

    /**
     * Where the measured child's start edge sits in the reported box: 0,
     * start-anchored, so an oversized box overflows toward the END edge
     * (css-overflow-3 §3, as the browser draws #middle) — never Compose's
     * requiredWidth centring at `(reported − child) / 2`.
     */
    @Suppress("UNUSED_PARAMETER") // the inputs name what the rule ignores
    fun placementOffset(childPx: Int, reportedPx: Int): Int = 0
}

/**
 * The modifier half: one axis of [PercentContentBoxBand] as a LayoutModifier
 * (data class: equal inputs ⇒ an equal node, so recomposition does not
 * re-layout). [bandCssPx] is the CSS-px band (Dp in this runtime).
 */
internal data class PercentFillPlusBandModifier(
    // The axis fraction (PercentContentBoxBand.fraction).
    val fraction: Float,
    // contentBoxInflateX / Y: padding + painted border widths, CSS px.
    val bandCssPx: Float,
    // true → width axis, false → height axis.
    val horizontal: Boolean,
) : LayoutModifier {

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        // CSS px → device px through the measure scope's density (roundToPx parity is in targetPx's rounding).
        val bandPx = bandCssPx.dp.toPx()
        // The incoming band on this axis only; the other axis passes through.
        val spec = if (horizontal)
            PercentContentBoxBand.measureSpec(fraction, bandPx, constraints.minWidth, constraints.maxWidth)
        else PercentContentBoxBand.measureSpec(fraction, bandPx, constraints.minHeight, constraints.maxHeight)
        // Unbounded: FillNode's identity — measure and report the child as is.
        if (spec == null) {
            // The incoming constraints, untouched.
            val p = measurable.measure(constraints)
            // Origin placement, as FillNode's layout().
            return layout(p.width, p.height) { p.placeRelative(0, 0) }
        }
        // Child size to measure at, and size to report upward.
        val (childPx, reportedPx) = spec
        // Tight on this axis at the target (may exceed the incoming max: overflow).
        val p = measurable.measure(
            if (horizontal) constraints.copy(minWidth = childPx, maxWidth = childPx)
            else constraints.copy(minHeight = childPx, maxHeight = childPx),
        )
        // Start-anchored offset of the (possibly oversized) placeable.
        val offset = PercentContentBoxBand.placementOffset(childPx, reportedPx)
        // Report the clamped size on this axis, the child's own on the other;
        // placeRelative mirrors the x offset under RTL (inline-end overflow).
        return if (horizontal) layout(reportedPx, p.height) { p.placeRelative(offset, 0) }
        else layout(p.width, reportedPx) { p.placeRelative(0, offset) }
    }
}

/**
 * Chain [PercentFillPlusBandModifier] for one axis of [raw] — SizingApplier's
 * call site, taken only when [PercentContentBoxBand.owns] said yes.
 */
internal fun Modifier.percentFillPlusBand(raw: LengthValue?, bandCssPx: Float, horizontal: Boolean): Modifier =
    // The fraction is computed exactly as the fill branch would have.
    this.then(PercentFillPlusBandModifier(PercentContentBoxBand.fraction(raw), bandCssPx, horizontal))
