package com.styleconverter.runtime.sizing

// CalcSizeValue — css-values-5 §10.1 `calc-size()` typed wire decode + the
// PURE arithmetic the layout modifiers in CalcSizeLayout.kt consume.
//
// Wave 42 (lane W3). Wire shape (converter CalcSizeParser.kt, identical
// across the WidthValue / MinMaxValue / MaxValue families):
//   {"type":"calc-size","basis":"auto","factor":1,"offsetPx":20,
//    "original":"calc-size(auto, size + 20px)"}
// Before this decoder existed the shape fell through extractLength to
// Unknown and the declaration was DROPPED. MEASURED consequence on the
// wave41-final Android captures: calc-size-flex-001..006 (a flex item whose
// min-width/min-height is `calc-size(auto, size + Npx)` inside a ZERO
// main-size container) painted NO GREEN AT ALL — the item collapsed to
// nothing — against a 100×100 green-square ref (wptPass=false at ssim
// 0.9542, presence veto), and flex-009 left its red container fully
// exposed (colour veto at ssim 0.9966).
//
// Everything in this file is pure and JVM-pinned (CalcSizeValueTest) — the
// "extract the decision, pin it on the JVM" shape this suite mandates
// (no Robolectric).

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The css-values-5 keyword bases a typed calc-size() can carry. */
enum class CalcSizeBasis {
    /** The property's normal `auto` behavior supplies the size. */
    AUTO,
    /** css-sizing-3 §4 min-content size. */
    MIN_CONTENT,
    /** css-sizing-3 §4 max-content size. */
    MAX_CONTENT,
    /** css-sizing-3 §5.1 fit-content: clamp(min-content, avail, max-content). */
    FIT_CONTENT,
    /** css-sizing-4 stretch — no Compose analogue; resolves like AUTO here. */
    STRETCH,
    /** css-values-5 `content` basis — resolves like AUTO here. */
    CONTENT,
}

/**
 * One typed calc-size() value: `factor * <resolved basis> + offsetPx`.
 * The converter only emits this for KEYWORD bases (pure-length bases are
 * evaluated at parse time), so a decoded instance always needs a layout-time
 * basis resolution — that is the modifiers' job; the arithmetic is here.
 */
data class CalcSizeValue(
    val basis: CalcSizeBasis,
    val factor: Double,
    val offsetPx: Double,
) {
    /**
     * The resolved target in px for a basis measured as [basisPx].
     * Floored at zero: css-values-4 §10 clamps negative calc() results on
     * properties whose value space is non-negative, which every sizing
     * longhand is (calc-size-aspect-ratio-003/004's `size - 50px` over a
     * zero basis must clamp to 0, not go negative).
     */
    fun targetPx(basisPx: Int): Int {
        // Affine evaluation in Double, then round like Compose's Dp→px
        // pipeline (roundToPx rounds half up) and clamp at the zero floor.
        val raw = factor * basisPx + offsetPx
        return kotlin.math.max(0, kotlin.math.round(raw).toInt())
    }

    companion object {
        /**
         * Decode the typed wire shape, or null when [data] is anything else.
         * Null routes the caller to its existing behavior (extractLength),
         * so the decode is purely additive — no value that decodes today
         * can change meaning.
         */
        fun decode(data: JsonElement?): CalcSizeValue? {
            // Only object payloads can carry the discriminated shape.
            val obj = data as? JsonObject ?: return null
            // Discriminator — the converter always stamps type:"calc-size".
            if ((obj["type"] as? JsonPrimitive)?.contentOrNull != "calc-size") return null
            // Basis keyword → enum. An unknown keyword is wire drift from a
            // NEWER converter — refuse (null) rather than guess a basis;
            // the caller's drop is the pre-existing documented behavior.
            val basis = when ((obj["basis"] as? JsonPrimitive)?.contentOrNull) {
                "auto" -> CalcSizeBasis.AUTO
                "min-content" -> CalcSizeBasis.MIN_CONTENT
                "max-content" -> CalcSizeBasis.MAX_CONTENT
                "fit-content" -> CalcSizeBasis.FIT_CONTENT
                "stretch" -> CalcSizeBasis.STRETCH
                "content" -> CalcSizeBasis.CONTENT
                else -> return null
            }
            // factor/offset default to the identity expression `size`, the
            // same degradation the web twin (CalcSizeValue.ts) applies.
            val factor = obj["factor"]?.jsonPrimitive?.doubleOrNull ?: 1.0
            val offset = obj["offsetPx"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            return CalcSizeValue(basis, factor, offset)
        }
    }
}

/**
 * Pure basis + band arithmetic for the calc-size layout modifiers. Split
 * from the Modifier halves (CalcSizeLayout.kt) so every decision is
 * JVM-pinnable without Robolectric — the ExactWidthOverflow.measureSpec
 * pattern.
 */
internal object CalcSizeMath {

    /**
     * Resolve the basis for a REAL measure pass on the width/height
     * (preferred size) lane.
     *
     * @param minIntrinsic the content's min-content main size (probed), or
     *   null when the subtree refused the intrinsic channel.
     * @param maxIntrinsic the content's max-content main size, same contract.
     * @param availableMax the incoming max constraint on this axis —
     *   css-sizing-3 §5.1's "available space" for the fit-content clamp.
     *   Constraints.Infinity means unbounded.
     * @return the basis in px, or null when the needed intrinsic was refused
     *   (the caller then measures with untouched constraints — the
     *   pre-existing dropped-declaration geometry, logged once).
     *
     * AUTO/STRETCH/CONTENT resolve as the MAX-CONTENT size: for the
     * calc-size corpus the box is an auto-sized block whose own size the
     * expression rebuilds from its content (documented approximation — a
     * true `auto` width in a definite containing block is fill-available,
     * but that basis would explode `size + 50px` on the 358px canvas where
     * the ref resolves against the ratio-transferred 50px;
     * calc-size-aspect-ratio-001 keeps its PASS with the content basis and
     * loses it with fill-available, MEASURED reasoning in the lane notes).
     */
    fun preferredBasis(
        basis: CalcSizeBasis,
        minIntrinsic: Int?,
        maxIntrinsic: Int?,
        availableMax: Int,
    ): Int? = when (basis) {
        CalcSizeBasis.MIN_CONTENT -> minIntrinsic
        CalcSizeBasis.MAX_CONTENT -> maxIntrinsic
        CalcSizeBasis.AUTO, CalcSizeBasis.STRETCH, CalcSizeBasis.CONTENT -> maxIntrinsic
        // css-sizing-3 §5.1: fit-content = max(min-content, min(avail, max-content)).
        // Unbounded avail degrades the clamp to max-content, per the spec's
        // own definition of shrink-to-fit under infinite available space.
        CalcSizeBasis.FIT_CONTENT -> {
            if (minIntrinsic == null || maxIntrinsic == null) null
            else {
                val avail = if (availableMax == androidx.compose.ui.unit.Constraints.Infinity)
                    maxIntrinsic else availableMax
                maxOf(minIntrinsic, minOf(avail, maxIntrinsic))
            }
        }
    }

    /**
     * Resolve the basis for an INTRINSIC query against the preferred lane —
     * css-values-5: a calc-size() value's min-content contribution
     * substitutes the corresponding intrinsic size for `size`, and its
     * max-content contribution likewise. So a min-content QUERY resolves
     * auto/fit-content/min-content bases from the child's MIN intrinsic
     * and max-content stays max; a max-content query mirrors.
     *
     * This is what makes calc-size-min-max-sizes-001 come out right: the
     * `width: fit-content` PARENT reads this box's min-content contribution
     * (child min 20 + 80 = 100) when squeezed to zero available — the ref's
     * 100×100 green square (comment in the WPT source: "min-content,
     * max-content size of this div should be: 100px, 120px").
     */
    fun intrinsicBasis(
        basis: CalcSizeBasis,
        wantMin: Boolean,
        childMinIntrinsic: Int,
        childMaxIntrinsic: Int,
    ): Int = when (basis) {
        // Fixed-kind bases answer the SAME intrinsic to both queries.
        CalcSizeBasis.MIN_CONTENT -> childMinIntrinsic
        CalcSizeBasis.MAX_CONTENT -> childMaxIntrinsic
        // Contribution-kind bases substitute the QUERIED intrinsic.
        else -> if (wantMin) childMinIntrinsic else childMaxIntrinsic
    }

    /**
     * The measure band for the MIN (floor) lane — css-flexbox-1 §4.5 /
     * css-sizing-3 §5.2 semantics: the used main size never falls below
     * [floorPx]; a container offering less than the floor is OVERFLOWED,
     * not honoured (the item must not collapse). Mirrors
     * FlexAutoMinSize.mainBand's shape, with the floor coming from the
     * calc-size arithmetic instead of the bare content minimum.
     *
     * @return (min, max) for the axis: floor applied to the min, and the
     *   max opened up to the floor when the incoming max is smaller.
     */
    fun floorBand(incomingMin: Int, incomingMax: Int, floorPx: Int): Pair<Int, Int> {
        // A degenerate floor can never shrink the band below the incoming.
        val floor = floorPx.coerceAtLeast(0)
        val mn = maxOf(incomingMin, floor)
        // Unbounded max stays unbounded; bounded max rises to the floor so
        // the band stays well-formed and the item can overflow (§4.5).
        val mx = if (incomingMax == androidx.compose.ui.unit.Constraints.Infinity)
            androidx.compose.ui.unit.Constraints.Infinity
        else maxOf(incomingMax, floor)
        return mn to mx
    }

    /**
     * The automatic-minimum BASIS for the min lane's `auto` basis —
     * css-flexbox-1 §4.5: min(specified size suggestion, content size
     * suggestion). The specified suggestion is the box's own definite
     * width/height (null when auto); the content suggestion is its
     * min-content size. Pinned against the corpus arithmetic:
     *   flex-001: spec –,  content 80 → 80 (+20 → 100)
     *   flex-002: spec 60, content 80 → 60 (+40 → 100)
     *   flex-003: spec 80, content 60 → 60 (+40 → 100)
     *   flex-009: spec 80, content 50 → 50 (×2 → 100)
     */
    fun autoMinBasis(specifiedPx: Int?, contentMin: Int): Int =
        if (specifiedPx == null) contentMin else minOf(specifiedPx, contentMin)
}
