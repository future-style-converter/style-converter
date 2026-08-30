package com.styleconverter.runtime.layout.position

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.Modifier
// The value form of the offset this applier emits — see [resolvedOffset],
// which the backdrop draw node reads because it sits OUTER of this step.
import androidx.compose.ui.unit.DpOffset
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

        // Wave 49 (lane A7) — the PERCENTAGE-INSET lane. A percentage inset
        // resolves against the containing block's corresponding dimension
        // (CSS 2.1 §9.4.3), a number only composition can see: it rides
        // `LocalContainingBlock`, which this static chain cannot read. The
        // composed detour therefore lives in its own file (the per-file size
        // rule) — [percentInsetPositioned] — and is entered ONLY when a side
        // was actually declared as a percentage, so every px-inset component
        // keeps the pre-wave-49 modifier chain exactly.
        val pct = config.percent
        if (pct != null && pct.any) return modifier.percentInsetPositioned(config)

        return applyResolvedPosition(modifier, config)
    }

    /**
     * The z-index + offset chain for a config whose insets are already
     * absolute Dp values. Split out of [applyPosition] so the percentage
     * lane above can run the identical chain on its resolved copy — the
     * two can never drift apart.
     */
    internal fun applyResolvedPosition(modifier: Modifier, config: PositionConfig): Modifier {
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
     * The offset [applyPosition] will actually emit for [config], as a value
     * instead of a modifier — the exact analogue of
     * `MarginApplier.resolvedInsets`.
     *
     * ## Why this exists (wave-27 backdrop fix)
     * `StyleApplier` installs the effects step (step 3) OUTSIDE the layout
     * step (step 4), and this applier's `absoluteOffset` is the last thing
     * step 4 chains. A draw node at step 3 is therefore OUTER of the offset:
     * its `positionInWindow()` and its local draw origin both describe the
     * element's UN-offset slot, while the element's own background/borders
     * (steps 5–6, INNER of the offset) paint at the offset one.
     *
     * The backdrop lane is the one consumer that has to reconcile the two —
     * it samples the canvas at a window coordinate AND paints into that draw
     * space, so a dropped offset mis-places both halves *consistently* (the
     * patch looked like a perfect filter of the wrong rectangle: measured on
     * `backdrop-filter-basic.html`, the inverted patch landed at the parent
     * colorbox's origin, exactly the child's `left:50px; top:50px` short).
     *
     * Returning the value from the same `config.offsetX`/`offsetY` reads
     * [applyOffset] uses — behind the same `hasPosition`/STATIC gates — means
     * the backdrop node and the layout chain can never disagree about how far
     * the box moved. Zero for a static (or offsetless) element, which is the
     * overwhelming majority and costs them nothing.
     *
     * KNOWN LIMIT (wave 49, lane A7) — stated rather than hidden, and the
     * exact shape MarginApplier.resolvedInsets already documents for its own
     * percent lane: a PERCENTAGE inset ([PositionConfig.percent]) is resolved
     * only inside [percentInsetPositioned]'s composed lane, which this plain
     * function cannot reproduce (it has no CompositionLocal scope). A percentage-inset
     * element that ALSO carries backdrop-filter / filter / box-shadow /
     * mix-blend-mode would therefore have its effect painted at the legacy
     * number-as-pixels offset while its own paint sits at the resolved one.
     * No corpus test combines the two: the only bare-number (percentage)
     * insets in all 30 frozen wave-48 sections are css-position/
     * position-relative-001…006 and -008, none of which declare an effect.
     * The fix when one arrives is to thread the resolved offset out of the
     * composed lane, exactly as the margin note prescribes.
     */
    fun resolvedOffset(config: PositionConfig): DpOffset {
        // Gate 1 — mirrors applyPosition's own first line: no positioning
        // properties at all means no modifier was chained, so no offset.
        if (!config.hasPosition) return DpOffset.Zero
        // Gate 2 — mirrors the STATIC branch of applyPosition's `when`: a
        // static box ignores its insets entirely (css-position-3 §2), and is
        // the only type that does NOT route through applyOffset.
        if (config.type == PositionType.STATIC) return DpOffset.Zero
        // Every other type (relative / absolute / fixed / sticky) calls
        // applyOffset, which reads exactly these two accessors.
        return DpOffset(config.offsetX, config.offsetY)
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
        //
        // Wave 28 (lane NE): there are TWO such slots now. The canvas-hoist
        // overlay owns ROOT-level boxes; a box with a POSITIONED ANCESTOR is
        // mounted by [PositionedAncestorAnchor] instead (the wave-8/9
        // RenderAbsoluteChild path, which anchored every child at the slot
        // origin and so painted `right:20; bottom:20` at (−20,−20)). Both
        // slots delegate to the same EndInsetAnchor arithmetic, so this
        // modifier's half of the composition is unchanged for either.
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
