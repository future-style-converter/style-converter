package com.styleconverter.runtime.rendering

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * [ZoomConfig] → a Compose [Modifier] implementing bounded css-viewport-1
 * `zoom` semantics.
 *
 * ## Why a plain `Modifier.scale` is the WRONG answer
 * `Modifier.scale` / a bare `graphicsLayer { scaleX = f }` warps PAINT
 * only: the parent keeps measuring the unzoomed box, so a zoomed widget
 * overflows its slot (or gets clipped by it) and every sibling below it
 * stays put. CSS `zoom` moves the slot too — it multiplies the element's
 * used values AND the space it occupies (css-viewport-1 §"The zoom
 * property"). That mismatch is exactly why this applier used to not exist
 * and the natives shipped an honest no-op + TODO.
 *
 * ## The mechanism: MEASURE-THEN-SCALE
 * The only primitive that can move a Compose layout slot is a custom
 * [Modifier.layout] node, so:
 *
 *  1. **De-scale the incoming constraints** (`constraints / zoom`). This
 *     is what makes a zoomed block box behave like the browser's: under
 *     `zoom: 1.5` the element still fits the parent's 390px, because in
 *     its OWN (zoomed) coordinate space the available room is 260px. It
 *     is also what makes percentages/`fillMaxWidth` inside resolve
 *     against the de-scaled containing block, matching the used-value
 *     rule.
 *  2. **Measure the subtree** at those constraints — every length inside
 *     (padding bands, border widths, the 50×30 placeholder floor, text
 *     line boxes) is still expressed in unzoomed dp, so it lands at its
 *     authored size in the child's coordinate space.
 *  3. **Report `size * zoom`** to the parent. That is the slot change:
 *     the next sibling in a column now starts `height * zoom` lower,
 *     exactly like the browser's zoomed block box.
 *  4. **Scale the paint by the same factor, anchored top-left**
 *     ([TransformOrigin] `(0, 0)`), so the pixels fill the slot step 3
 *     just claimed. Top-left is the correct anchor because CSS zoom is
 *     not a centred transform — the element's border-box ORIGIN stays put
 *     and the box grows right/down from it.
 *
 * Steps 2–4 compose exactly the way CSS composes effective zoom: a
 * `zoom: 2` box nested in a `zoom: 1.5` box gets 1.5 from the ancestor's
 * layer and 2 from its own → 3×, which is what the browser's inherited
 * effective zoom produces.
 *
 * ## Fidelity note (stated, not hidden)
 * The browser re-lays-out text at `font-size × zoom`, so glyph hinting
 * and line breaking are recomputed. Here the subtree is laid out at the
 * unzoomed font size and the RenderNode transform magnifies it. Android's
 * `graphicsLayer` records a display list and applies the scale as a
 * canvas matrix, so glyphs are re-rasterised at the scaled size (not a
 * bitmap blow-up) and the result is crisp — but a line that would have
 * broken differently at the zoomed font size will not. No fixture in the
 * corpus exercises that; when one does, the fix is font-size threading,
 * not a change to this node. `runtimes/swiftui/.../rendering/
 * ZoomApplier.swift` is the byte-parallel twin.
 */
object ZoomApplier {

    /**
     * Chains the zoom node. Identity when the config carries no scale, so
     * every un-zoomed element in the corpus keeps a byte-identical chain.
     *
     * MUST be chained OUTERMOST (Compose modifier order is outer → inner,
     * so "outermost" = first). Everything the element paints — background,
     * borders, padding band, sizing frame, margin — has to sit inside this
     * node for the used-value multiplication to cover it.
     */
    fun applyZoom(modifier: Modifier, config: ZoomConfig): Modifier {
        if (!config.hasZoom) return modifier
        val f = config.zoom
        // Belt-and-braces: the extractor already rejects <= 0 / non-finite
        // factors, but this node divides by `f` and a bad value here would
        // produce negative or NaN constraints deep inside the measure pass.
        if (!f.isFinite() || f <= 0f) return modifier

        return modifier
            .layout { measurable, constraints ->
                // Step 1 — de-scale. Infinity must survive as Infinity:
                // dividing Int.MAX_VALUE by 0.75 overflows to a value
                // Constraints() rejects, and an intrinsic/unbounded pass
                // (Column height, scrollable content) legitimately hands
                // us unbounded constraints.
                val maxW = descale(constraints.maxWidth, f)
                val maxH = descale(constraints.maxHeight, f)
                // Mins are de-scaled the same way, then clamped under the
                // max so integer rounding can never invert the pair (a
                // Constraints with minWidth > maxWidth throws).
                val minW = descale(constraints.minWidth, f).coerceAtMost(maxW)
                val minH = descale(constraints.minHeight, f).coerceAtMost(maxH)

                // Step 2 — measure the subtree in its own zoomed space.
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = minW, maxWidth = maxW,
                        minHeight = minH, maxHeight = maxH
                    )
                )

                // Step 3 — report the scaled slot. Rounded (not truncated)
                // so a 30dp floor at 0.75 reports 23, matching the
                // browser's 22.5 → 23 device-pixel snap on the same box.
                val w = (placeable.width * f).roundToInt().coerceAtLeast(0)
                val h = (placeable.height * f).roundToInt().coerceAtLeast(0)
                layout(w, h) {
                    // Origin-anchored: the child's own top-left coincides
                    // with the slot's, and step 4 grows the paint from
                    // there.
                    placeable.place(0, 0)
                }
            }
            // Step 4 — paint scale. Chained INSIDE the layout node (later
            // in the chain = inner), so this graphics layer wraps only the
            // subtree, and the layout node above sees the child's unscaled
            // measured size (graphicsLayer is size-transparent).
            .graphicsLayer {
                scaleX = f
                scaleY = f
                // (0,0) = the border-box origin. `.scale()` would centre
                // the transform and slide the box left/up by half the
                // growth, which is not what CSS zoom does.
                transformOrigin = TransformOrigin(0f, 0f)
                // Explicitly NOT clipping: a zoomed element whose content
                // overflows keeps painting outside, exactly as the browser
                // does (the harness label spills past the right edge of
                // every Zoom_* box on web).
                clip = false
            }
    }

    /**
     * `value / factor`, preserving [Constraints.Infinity] and staying
     * inside the range `Constraints` can bit-pack. Public for the unit
     * pins — the rounding rule here is the whole reason the reported slot
     * matches the browser's.
     */
    internal fun descale(value: Int, factor: Float): Int {
        if (value == Constraints.Infinity) return Constraints.Infinity
        val scaled = value / factor.toDouble()
        // A factor < 1 GROWS the available room; cap below Infinity so the
        // result never accidentally reads as "unbounded".
        if (scaled >= Constraints.Infinity.toDouble()) return Constraints.Infinity - 1
        return scaled.roundToInt().coerceAtLeast(0)
    }
}
