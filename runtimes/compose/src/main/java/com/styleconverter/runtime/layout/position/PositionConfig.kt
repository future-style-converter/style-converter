package com.styleconverter.runtime.layout.position

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for CSS positioning properties.
 *
 * Handles position type (static, relative, absolute, fixed, sticky) and
 * offset values (top, right, bottom, left, inset-*).
 *
 * ## Compose Mapping
 * - `position: relative` -> `Modifier.absoluteOffset()`
 * - `position: absolute` -> `Box` with `Modifier.absoluteOffset()` (needs container handling)
 * - `position: fixed` -> canvas-root overlay anchor ([CanvasRootHoist], wave 17)
 * - `position: sticky` -> Limited support (scroll-aware behavior)
 *
 * ## Logical Properties
 * Logical properties (inset-block-*, inset-inline-*) are mapped to physical
 * properties for LTR horizontal writing mode:
 * - inset-block-start -> top
 * - inset-block-end -> bottom
 * - inset-inline-start -> start (left in LTR)
 * - inset-inline-end -> end (right in LTR)
 */
data class PositionConfig(
    val type: PositionType = PositionType.STATIC,
    val top: Dp? = null,
    val end: Dp? = null,
    val bottom: Dp? = null,
    val start: Dp? = null,
    // Logical properties (writing mode aware)
    val insetBlockStart: Dp? = null,
    val insetBlockEnd: Dp? = null,
    val insetInlineStart: Dp? = null,
    val insetInlineEnd: Dp? = null,
    val zIndex: Float = 0f
) {
    /**
     * Returns true if any positioning property is defined.
     */
    val hasPosition: Boolean
        get() = type != PositionType.STATIC ||
            top != null || end != null || bottom != null || start != null ||
            insetBlockStart != null || insetBlockEnd != null ||
            insetInlineStart != null || insetInlineEnd != null ||
            zIndex != 0f

    /**
     * Returns true if z-index is explicitly set to a non-zero value.
     */
    val hasZIndex: Boolean
        get() = zIndex != 0f

    /**
     * Returns true if this element needs absolute positioning (removed from flow).
     */
    val isAbsolutelyPositioned: Boolean
        get() = type == PositionType.ABSOLUTE || type == PositionType.FIXED

    // Resolved offsets (physical property takes precedence over logical)
    val resolvedTop: Dp?
        get() = top ?: insetBlockStart

    val resolvedEnd: Dp?
        get() = end ?: insetInlineEnd

    val resolvedBottom: Dp?
        get() = bottom ?: insetBlockEnd

    val resolvedStart: Dp?
        get() = start ?: insetInlineStart

    /**
     * Wave 22 (B-RC3) — does the INLINE axis anchor from the END (right)
     * edge of the containing block?
     *
     * css-position-3 §3.5.3: with `left: auto` and `right` non-auto, the
     * used inline position is measured from the containing block's RIGHT
     * edge — `right: 0` on a 100px box inside a 390px containing block
     * paints at x = 290, NOT at x = 0. When BOTH sides are declared the box
     * is over-constrained and, in an LTR containing block, `left` wins
     * (§3.5.3's "ignore right" rule) — so this predicate is false and the
     * start anchor keeps owning the axis.
     *
     * The paired [offsetX] is the SIGNED inset from whichever anchor this
     * predicate selects (`-right` on an end anchor), so the two compose to
     * `cbWidth − boxWidth − right`; [EndInsetAnchor] documents and pins the
     * composition, and CanvasRootHoist's overlay slot applies it. Consumers
     * that cannot resolve a containing-block extent keep placing at the
     * start anchor — the pre-wave-22 behavior, honestly degraded.
     *
     * The `end`/`insetInlineEnd` merge below is the engine's documented
     * LTR-horizontal-tb normalization (see the class kdoc): logical
     * inline-end is folded into the physical right slot at extract time.
     *
     * ONLY out-of-flow boxes ([isAbsolutelyPositioned]) anchor this way.
     * For `position: relative` an inset is NOT an anchor at all — it is a
     * displacement from the box's STATIC position (css-position-3 §3.4:
     * `bottom: 160px` moves the box UP 160px and reserves its original
     * flow space), which [offsetY]'s `-bottom` already renders correctly
     * from the start anchor. Without this gate the predicate answers true
     * for such a box and any future consumer that anchors on it would
     * teleport it to the containing block's end edge — a live corpus
     * witness exists today: css-backgrounds/background-334's root __2 is
     * `position: relative; bottom: 160px`. It never reaches the anchor now
     * (CanvasRootHoist only hoists absolute/fixed), so this gate is
     * byte-neutral for every current caller and purely a guard for the
     * nested-containing-block lane that is expected to consume it next.
     * `sticky` is excluded for the same reason — its insets are scroll
     * thresholds, not containing-block anchors (css-position-3 §3.6).
     */
    val anchorsFromEndX: Boolean
        get() = isAbsolutelyPositioned && resolvedStart == null && resolvedEnd != null

    /**
     * Block-axis twin of [anchorsFromEndX] (css-position-3 §3.5.3 applied
     * to the block axis): `top: auto` + a declared `bottom` anchors the box
     * at the containing block's BOTTOM edge — `bottom: 0` on a 100px box in
     * a 600px containing block paints at y = 500. A declared `top` wins
     * when both are present (over-constrained resolution). Gated on
     * [isAbsolutelyPositioned] for the reason spelled out on [anchorsFromEndX].
     */
    val anchorsFromEndY: Boolean
        get() = isAbsolutelyPositioned && resolvedTop == null && resolvedBottom != null

    /**
     * Wave 28 (lane NE) — does EITHER axis anchor from the containing
     * block's end edge? The one gate a mounting slot needs to decide
     * between the start-anchored placement (every frozen baseline) and the
     * end-anchored mount ([PositionedAncestorAnchor.endAnchoredMeasure]).
     *
     * Deliberately an OR, not a per-axis pair: a mixed box (`left: 16;
     * bottom: 0` — EndInsetAnchorTest's A3 mixed case) needs the anchored
     * mount for its block axis while its inline axis keeps the slot origin,
     * and the mount resolves the two axes independently. Both predicates are
     * already gated on [isAbsolutelyPositioned], so relative / sticky /
     * static boxes — whose insets are displacements or scroll thresholds,
     * never containing-block anchors — answer false here too.
     */
    val anchorsFromEndAnyAxis: Boolean
        get() = anchorsFromEndX || anchorsFromEndY

    /**
     * Calculate horizontal offset for positioning.
     * Positive values move right, negative values move left.
     *
     * For CSS `left: 10px`, the element moves right by 10px.
     * For CSS `right: 10px`, the element moves left by 10px (from the right edge).
     *
     * When both are specified, `left` takes precedence (CSS behavior).
     *
     * NOTE (wave 22, B-RC3): the `right`/`bottom` branches return a NEGATED
     * inset, which is only correct RELATIVE TO THE END EDGE — the caller
     * must first anchor the box there ([anchorsFromEndX]/[anchorsFromEndY]
     * + [EndInsetAnchor]). Applied from a top-left anchor it lands
     * `bottom: 0; right: 0` at (0,0) — the diagnosed multicol defect where
     * the bottom-right red div painted UNDER the top-left one (Android red
     * ink was exactly one 100×100 for two red divs; the web oracle paints
     * them at (0,0) and (290,500) on the 390×600 canvas).
     */
    val offsetX: Dp
        get() {
            val leftOffset = resolvedStart
            val rightOffset = resolvedEnd

            return when {
                // Left takes precedence when both are specified
                leftOffset != null -> leftOffset
                rightOffset != null -> -rightOffset
                else -> 0.dp
            }
        }

    /**
     * Calculate vertical offset for positioning.
     * Positive values move down, negative values move up.
     *
     * For CSS `top: 10px`, the element moves down by 10px.
     * For CSS `bottom: 10px`, the element moves up by 10px (from the bottom edge).
     *
     * When both are specified, `top` takes precedence (CSS behavior).
     */
    val offsetY: Dp
        get() {
            val topOffset = resolvedTop
            val bottomOffset = resolvedBottom

            return when {
                // Top takes precedence when both are specified
                topOffset != null -> topOffset
                bottomOffset != null -> -bottomOffset
                else -> 0.dp
            }
        }
}

/**
 * CSS position types and their Compose mappings.
 */
enum class PositionType {
    /**
     * Normal document flow. No offset applied.
     * Default behavior in both CSS and Compose.
     */
    STATIC,

    /**
     * Offset from normal position without affecting layout.
     * Maps to `Modifier.offset()` in Compose.
     */
    RELATIVE,

    /**
     * Removed from flow, positioned relative to nearest positioned ancestor.
     * In Compose, requires `Box` container handling.
     */
    ABSOLUTE,

    /**
     * Removed from flow, positioned relative to the viewport — the capture
     * canvas (css-position-3 §3.2). Wave 17: hoisted to the canvas-root
     * overlay by [CanvasRootHoist]; NOT a Popup (a separate window never
     * composites into the capture bitmap).
     */
    FIXED,

    /**
     * Hybrid: static until scroll threshold, then fixed.
     * Requires scroll-aware behavior, limited support in basic Compose.
     */
    STICKY
}
