package com.styleconverter.runtime.layout.position

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Applies position configuration to a Compose Modifier.
 *
 * ## CSS Position Mapping
 *
 * | CSS Position | Compose Equivalent |
 * |--------------|-------------------|
 * | static       | No modifier (default) |
 * | relative     | `Modifier.absoluteOffset()` (CSS insets are physical) |
 * | absolute     | `Box` + `Modifier.absoluteOffset()` (container handling required) |
 * | fixed        | `Modifier.absoluteOffset()` from a [CanvasRootHoist] overlay anchor |
 * | sticky       | [StickyPositionWrapper] with scroll-aware behavior |
 *
 * ## Implementation Notes
 *
 * 1. **Absolute positioning**: In CSS, `position: absolute` removes the element from
 *    the normal flow and positions it relative to the nearest positioned ancestor.
 *    In Compose, this requires the parent to be a `Box` and uses
 *    `Modifier.absoluteOffset()` (physical CSS insets, css-position-3 §3.1).
 *    The [needsAbsoluteContainer] method indicates when this is needed. With NO
 *    positioned ancestor the containing block is the initial containing block —
 *    the capture canvas — and [CanvasRootHoist] re-anchors the box there
 *    (wave 17, pin S2); the wave-8/9 positioned-container machinery keeps
 *    owning the positioned-ancestor case (pin S4).
 *
 * 2. **Fixed positioning** (wave 17): CSS `position: fixed` anchors at the
 *    VIEWPORT (css-position-3 §3.2) — our capture canvas. [CanvasRootHoist]
 *    hoists the box out of its parent's flow entirely and renders it from a
 *    zero-size overlay anchor at the UNPADDED canvas origin; the FIXED branch
 *    below then applies the (left, top) inset as an absoluteOffset from that
 *    origin. The former Popup-based FixedPositionWrapper was deleted as dead
 *    code — do NOT resurrect the Popup route: a Popup renders into a SEPARATE
 *    WINDOW that never composites into the capture bitmap (PixelCopy and the
 *    composed canvas's GraphicsLayer record only the canvas's own window
 *    surface), so a Popup-fixed box is invisible to every capture pipeline.
 *
 * 3. **Sticky positioning**: CSS `position: sticky` is a hybrid that acts like
 *    relative until a scroll threshold, then acts like fixed. Implemented via
 *    [StickyPositionWrapper] which tracks scroll state and applies dynamic offset.
 *
 * ## z-index
 *
 * CSS `z-index` maps directly to `Modifier.zIndex()` for controlling
 * draw order within the same parent.
 */
object PositionApplier {

    /**
     * Apply position configuration to a modifier.
     *
     * @param modifier The base modifier to extend
     * @param config The position configuration to apply
     * @return Modified modifier with positioning applied
     */
    fun applyPosition(modifier: Modifier, config: PositionConfig): Modifier {
        if (!config.hasPosition) return modifier

        var result = modifier

        // Apply z-index for stacking order
        if (config.hasZIndex) {
            result = result.zIndex(config.zIndex)
        }

        // Apply positioning based on type
        result = when (config.type) {
            PositionType.STATIC -> {
                // Static elements have no offset
                result
            }

            PositionType.RELATIVE -> {
                // Relative: offset from normal position
                applyOffset(result, config)
            }

            PositionType.ABSOLUTE -> {
                // Absolute: positioned relative to container
                // Note: This requires the parent to be a Box with proper alignment
                applyOffset(result, config)
            }

            PositionType.FIXED -> {
                // Fixed (wave 17): the containing block is the VIEWPORT —
                // our capture canvas (css-position-3 §3.2). The offset below
                // is the (left, top) INSET from wherever this node is
                // anchored: under CanvasRootHoist.Host (the composed WPT
                // canvas) the node renders from a zero-size overlay anchor
                // at the UNPADDED canvas origin, so this lands the box at
                // canvas (left, top) exactly like the Chromium reference
                // (pins S1/S3 — the parent's flow slot contributes NOTHING;
                // the pre-wave-17 parent-relative reading of this same
                // modifier was the diagnosed bug). On hostless paths (the
                // dark-stage per-component canvas, whose wave-1 out-of-flow
                // root contract already anchors a root-level subject) the
                // chain is byte-identical to the frozen 327-pair baseline.
                applyOffset(result, config)
            }

            PositionType.STICKY -> {
                // Sticky: hybrid behavior
                // Limited support - treat as relative for basic offset
                applyOffset(result, config)
            }
        }

        return result
    }

    /**
     * Apply offset based on resolved top/right/bottom/left values.
     *
     * This applies the calculated offsetX and offsetY from the config,
     * which already handles precedence rules (left over right, top over bottom).
     */
    private fun applyOffset(modifier: Modifier, config: PositionConfig): Modifier {
        // Wave 22 (B-RC3): these offsets are SIGNED insets from whichever
        // anchor the box's declared sides select — positive from the start
        // (left/top) anchor, NEGATIVE from the end (right/bottom) anchor
        // (css-position-3 §3.5.3). Choosing the anchor is deliberately NOT
        // this modifier's job: it has no containing-block extent and no
        // measured box size. The mounting slot owns it — CanvasRootHoist's
        // overlay anchors an end-only-inset box flush with the canvas edge
        // (cb − box) and this modifier's `−right` then pulls it inward to
        // the CSS used position cb − box − right. iOS composes the same two
        // halves (PositionApplier.anchoredInsetOffset: a `.trailing`/
        // `.bottom` frame alignment plus the identical negated offset), so
        // the two natives share one rule table. Where no slot resolves an
        // extent, the start anchor is kept — EndInsetAnchor's A4.
        val x = config.offsetX
        val y = config.offsetY

        // Only apply offset if there's actual movement
        return if (x.value != 0f || y.value != 0f) {
            // Wave 12: absoluteOffset, NOT offset. CSS top/right/bottom/left
            // are PHYSICAL insets (css-position-3 §3.1: `left` always
            // measures from the physical LEFT edge — only the logical
            // inset-inline-* spellings are direction-aware, and PositionConfig
            // already resolved those into the physical slots). This applier is
            // the LIVE render chain (StyleApplier → LayoutFacade →
            // applyPosition) and used the layout-direction-aware
            // Modifier.offset(), so under Direction:RTL (ComponentRenderer
            // provides LocalLayoutDirection=Rtl) a CSS left:-20px mirrored to
            // +20px physical: the three rtl abspos-autopos containers
            // pixel-measured at x56 instead of x16 (+40px total error).
            modifier.absoluteOffset(x = x, y = y)
        } else {
            modifier
        }
    }

    /**
     * Apply only z-index without any offset.
     * Useful when offset is handled separately (e.g., in container logic).
     *
     * @param modifier The base modifier
     * @param config The position configuration
     * @return Modifier with only z-index applied
     */
    fun applyZIndexOnly(modifier: Modifier, config: PositionConfig): Modifier {
        return if (config.hasZIndex) {
            modifier.zIndex(config.zIndex)
        } else {
            modifier
        }
    }

    /**
     * Check if this component needs special container handling for absolute/fixed positioning.
     *
     * When true, the parent container should:
     * 1. Use `Box` as the container type
     * 2. Position this child using alignment and offset
     * 3. Handle the element being "out of flow"
     *
     * @param config The position configuration to check
     * @return True if absolute container handling is needed
     */
    fun needsAbsoluteContainer(config: PositionConfig): Boolean {
        return config.isAbsolutelyPositioned
    }

    /**
     * Check if a property type is handled by this applier.
     */
    fun isPositionProperty(propertyType: String): Boolean {
        return PositionExtractor.isPositionProperty(propertyType)
    }

    // needsFixedWrapper / needsPositionWrapper were deleted in wave 17
    // together with the Popup-based FixedPositionWrapper they routed to
    // (dead code — no call sites; see the fixed-positioning note in the
    // header for why the Popup route must never come back). Fixed boxes now
    // ride CanvasRootHoist + the FIXED branch above.

    /**
     * Check if this config requires a StickyPositionWrapper.
     *
     * When true, the component should be wrapped with [StickyPositionWrapper]
     * instead of using normal modifier-based positioning.
     */
    fun needsStickyWrapper(config: PositionConfig): Boolean {
        return config.type == PositionType.STICKY
    }
}
