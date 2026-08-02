package com.styleconverter.runtime.layout.flexbox

// Wave 25 lane CFLEX — CAL-RC4: pin a flex item to its RESOLVED main size
// even when that size overflows the container.
//
// THE DEFECT THIS CLOSES: the non-wrapping Row/Column path runs the full
// css-flexbox-1 §9.7 loop (FlexSizeResolver) and then pinned each item
// with `Modifier.width(resolved)` / `Modifier.height(resolved)`. Compose's
// size modifiers CONSTRAIN their request into the incoming constraints,
// and Row/Column hand each child only the space LEFT on the main axis, so
// a line whose used sizes exceed the container silently squeezed its tail
// items: the 6×50px `flex-shrink: 0` items of css-gaps
// flex-gap-decorations-008 (200px nowrap container, 10px column-gap →
// 350px of content) rendered as four full items and two slivers, where
// §9.7.2 freezes every inflexible item at its base and the line simply
// OVERFLOWS (css-flexbox-1 §9.7 step 2 + css-overflow-3: overflow is a
// paint concern, not a sizing one).
//
// WHY THIS IS GENERAL AND NOT A shrink-0 SPECIAL CASE: `flex-shrink: 0` is
// only the most obvious way to produce an overflowing line. A min-width
// clamp (§9.7.4.d min violation — our items carry the web harness's 50px
// placeholder floor) overflows the same way, and CSS treats both
// identically: the resolver's output IS the used main size, full stop. So
// every resolved item takes this pin, and for the (overwhelmingly common)
// line that fits, the pin is byte-identical to the old
// Modifier.width/height — the forced constraints are already inside the
// incoming ones, so `constrain()` was a no-op there.
//
// The wave-9 INTRINSIC path (FlexIntrinsicLayout) never had this bug: it
// builds `Constraints(w, w, …)` from scratch inside its own Layout instead
// of going through a size modifier. This file gives the STATIC path the
// same measure semantics.

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * The constraints a resolved flex item is measured with.
 *
 * Pure so the JVM suite can pin the overflow contract without composing:
 * the main axis becomes EXACTLY [mainPx] regardless of what the parent
 * offered, while the cross axis passes the incoming band through unchanged
 * (cross sizing is `align-items`' business, resolved elsewhere).
 *
 * @param incoming    what Row/Column offered this child (main axis already
 *                    reduced by whatever earlier siblings consumed).
 * @param mainPx      the §9.7 used main size, in real pixels.
 * @param horizontal  true when the container's main axis is inline (Row).
 */
internal fun mainSizePinConstraints(
    incoming: Constraints,
    mainPx: Int,
    horizontal: Boolean
): Constraints {
    // Negative sizes are impossible per §9.7 (every clamp floors at min ≥ 0)
    // but the resolver works in Doubles, so guard the rounding boundary.
    val main = mainPx.coerceAtLeast(0)
    return if (horizontal) {
        // Fixed inline size; block band untouched so a stretch/align-items
        // decision made by the caller still reaches the child.
        Constraints(main, main, incoming.minHeight, incoming.maxHeight)
    } else {
        Constraints(incoming.minWidth, incoming.maxWidth, main, main)
    }
}

/**
 * Pin a row flex item's used INLINE size, overflowing the container when
 * §9.7 says it must.
 *
 * Intrinsics: a bare `Modifier.layout` node reports intrinsics by running
 * this same measure lambda against an intrinsic measurable, so an outer
 * intrinsic query (a nested FlexIntrinsicRow, a Compose IntrinsicSize)
 * still sees [width] — the property `Modifier.width` gave us for free.
 */
fun Modifier.flexMainWidthPin(width: Dp): Modifier = this.layout { measurable, constraints ->
    val target = width.roundToPx()
    val placeable = measurable.measure(mainSizePinConstraints(constraints, target, horizontal = true))
    // Report the child's own size: Row sums these for its content width and
    // then coerces ITSELF into its constraints, which is what makes the
    // surplus render as overflow instead of as a squeezed item.
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** Column twin of [flexMainWidthPin] — the main axis is BLOCK (height). */
fun Modifier.flexMainHeightPin(height: Dp): Modifier = this.layout { measurable, constraints ->
    val target = height.roundToPx()
    val placeable = measurable.measure(mainSizePinConstraints(constraints, target, horizontal = false))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
