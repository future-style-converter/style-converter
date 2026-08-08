package com.styleconverter.runtime.core.renderer

// Compose runtime — wave 35 lane B5, the renderer's VERTICAL-FLOW region.
//
// Two composables, both owned by `writing-mode` and nothing else. They are
// the ONLY place in the Compose renderer that turns a vertical writing mode
// into geometry; every decision they take comes from the pure twin module
// `typography/text/VerticalTextFlow.kt`, so the Kotlin and Swift renderers
// cannot drift about which runs are upright or where a vertical line breaks.
//
//   • [VerticalRotatedTextRun] — the wave-5 SIDEWAYS run, lifted verbatim out
//     of ComponentRenderer.PlaceholderContent. Measures the run against
//     SWAPPED constraints (its inline axis is the box's block axis) and spins
//     the laid-out result about its centre. Byte-identical to the code it
//     replaces: same swap, same coerce, same centre-rotate.
//   • [VerticalUprightTextFlow] — NEW. Typesets an UPRIGHT run as real
//     vertical lines: glyphs stack DOWN a line, lines stack across the block
//     axis (right-to-left for `vertical-rl`, left-to-right for `vertical-lr`).
//
// ## Why a second composable instead of a flag on the first
// A rotated run and an upright run need OPPOSITE transforms. The rotated path
// gets its line stacking for free — rotating a 4-line horizontal block by 90°
// turns the top line into the rightmost column — but every glyph comes along
// for the ride and ends up lying on its side. An upright run needs the line
// stacking WITHOUT the glyph rotation, which no single `rotationZ` can
// express. Measured on the frozen wave34-final capture of WPT
// css-writing-modes/available-size-011 (fullwidth "ＰＡＳＳ",
// Vertical_Orientation U ⇒ upright under the initial `text-orientation:
// mixed`): Android 0.9247 through the rotated path, web 0.9898 upright.
//
// ## The decline contract
// [VerticalUprightTextFlow] does its planning at MEASURE time, because the
// wrap budget is the incoming height constraint and composition cannot see
// it. When `VerticalTextFlow.uprightColumnIndices` declines — an unbounded
// height budget, a degenerate advance, a >64-line plan — this composable
// places the `rotatedRun` slot instead and the frame is exactly what the
// frozen renderer drew. Nothing is guessed.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.styleconverter.runtime.typography.text.GlyphOrientation
import com.styleconverter.runtime.typography.text.LineStack
import com.styleconverter.runtime.typography.text.VerticalTextFlow
import com.styleconverter.runtime.typography.text.WritingModeConfig

/**
 * THE GATE: does this run take the upright vertical path, and if so which way
 * do its lines stack? `null` = "no", and the caller must keep whatever it did
 * before — a horizontal mode, a sideways mode, an ASCII run under `mixed`, an
 * explicit `text-orientation: sideways`, or a run that needs both classes.
 *
 * One function so the decision has ONE spelling per platform; the Swift twin
 * is `VerticalUprightGate.stack(properties:text:)`, which reaches the same
 * two `VerticalTextFlow` calls through its own extractors.
 */
/** One-shot latch for the decline breadcrumb below (process-wide). */
private val uprightDeclineLogged = java.util.concurrent.atomic.AtomicBoolean(false)

internal fun verticalUprightStack(config: WritingModeConfig, text: String): LineStack? {
    val orientation = VerticalTextFlow.runOrientation(
        config.writingMode, config.textOrientation, text
    )
    if (orientation != GlyphOrientation.UPRIGHT) return null
    return VerticalTextFlow.lineStack(config.writingMode)
}

/**
 * The wave-5 SIDEWAYS vertical run: lay [run] out against swapped
 * constraints, then rotate it about its centre by [rotationDegrees].
 *
 * css-writing-modes-4 §3 changes the flow direction of the CONTENT, not the
 * box: the element keeps its specified width/height (that is why
 * `WritingModeApplier.applyWritingMode` returns the modifier untouched) and
 * only the glyph run turns. The run's inline axis is the box's BLOCK axis, so
 * it wraps at the incoming HEIGHT budget — hence the swap below.
 *
 * @param rotationDegrees +90 for the `-rl` family and `vertical-lr`
 *   (top-to-bottom glyph run), −90 for `sideways-lr` (which reads
 *   bottom-to-top). The caller owns that choice; this layout only applies it.
 */
@Composable
internal fun VerticalRotatedTextRun(
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
    run: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = run) { measurables, constraints ->
        // Swap the axes: the text's inline axis runs along the box's block
        // axis, so its wrap width is the incoming HEIGHT budget (unbounded →
        // let it be a single line).
        val swapped = Constraints(
            minWidth = 0,
            maxWidth = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity,
            minHeight = 0,
            maxHeight = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity,
        )
        val placeable = measurables.first().measure(swapped)
        // Report the rotated footprint (width↔height swapped).
        val w = placeable.height.coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = placeable.width.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(w, h) {
            // Center-rotate: place the child so its center lands at the
            // wrapper's center, then spin it about that center.
            val x = (w - placeable.width) / 2
            val y = (h - placeable.height) / 2
            placeable.placeWithLayer(x, y) {
                rotationZ = rotationDegrees
            }
        }
    }
}

/**
 * An UPRIGHT vertical run: glyphs stand up and stack DOWN each line, lines
 * stack across the block axis per [stack].
 *
 * The content carries BOTH candidate subtrees — slot 0 is [rotatedRun] (the
 * decline fallback), slots 1…n are one [uprightGlyph] per code point of
 * [text] — because the plan needs the incoming constraints and composition
 * runs before measurement. Exactly one subtree is ever PLACED, so only one
 * ever paints.
 *
 * TODO(wave-35 lane B5, honest gap): text-decoration lines and emphasis marks
 * are NOT carried onto an upright vertical run. The caller's `decorationModifier`
 * / `emphasisModifier` draw in the horizontal run's frame, and re-deriving
 * them per upright line box is a separate slice. No corpus test decorates an
 * upright vertical run today (the one that decorates a VERTICAL run,
 * css-text-decor/line-through-vertical, is ASCII ⇒ rotated ⇒ untouched by
 * this path).
 *
 * @param text          the run as the caller will paint it (post
 *   text-transform), split here into per-code-point glyph slots.
 * @param stack         which side line 1 sits on — `VerticalTextFlow.lineStack`.
 * @param rotatedRun    the frozen sideways path, placed verbatim on decline.
 * @param uprightGlyph  paints ONE code point upright, styled like the run.
 */
@Composable
internal fun VerticalUprightTextFlow(
    text: String,
    stack: LineStack,
    modifier: Modifier = Modifier,
    rotatedRun: @Composable () -> Unit,
    uprightGlyph: @Composable (String) -> Unit,
) {
    // One slot per code point, in logical order — the array the plan's
    // indices address. Memoised on the string so recomposition of an
    // unchanged run does not rebuild the list.
    val glyphs = remember(text) { VerticalTextFlow.codePointsOf(text) }
    Layout(
        modifier = modifier,
        content = {
            rotatedRun()                          // slot 0 — decline fallback
            glyphs.forEach { uprightGlyph(it) }   // slots 1 … n
        },
    ) { measurables, constraints ->
        // EVERY measurable is measured exactly once, on BOTH paths — including
        // the fallback the plan path never places. A `Measurable` may be
        // measured at most once per pass, and leaving one unmeasured is the
        // kind of half-initialised layout node that only misbehaves on a
        // device; measuring it costs one text layout and paints nothing,
        // because an unPLACED placeable is never drawn.
        val fallback = measurables.first().measure(constraints)
        // Every glyph measures FREE: an upright glyph is its own line box and
        // is never squeezed by the run's box (CSS overflows instead).
        val free = Constraints()
        val glyphPlaceables = measurables.drop(1).map { it.measure(free) }
        // The advance along the vertical inline axis = one line box's height.
        val advance = glyphPlaceables.firstOrNull()?.height?.toDouble() ?: 0.0
        // The wrap budget IS the incoming height constraint — null when the
        // block axis is unbounded, which the planner declines on.
        val budget = if (constraints.hasBoundedHeight) constraints.maxHeight.toDouble() else null
        val plan = VerticalTextFlow.uprightColumnIndices(glyphs, advance, budget)

        if (plan == null) {
            // No silent fallthrough: the GATE already said this run is
            // upright, so a decline here is a real, named gap and not a
            // routine "not our case". Logged once per process — the Swift
            // twin logs the same key through PropertyTracker.logOnce.
            if (uprightDeclineLogged.compareAndSet(false, true)) {
                android.util.Log.i(
                    "ComponentRenderer",
                    "writing-mode:upright-vertical-budget — upright vertical run " +
                        "declined: no finite block-axis budget " +
                        "(hasBoundedHeight=${constraints.hasBoundedHeight}, " +
                        "advance=$advance); run kept on the rotated path",
                )
            }
            // DECLINE — hand the frame back to the frozen sideways path. The
            // fallback was measured against the ORIGINAL constraints, so this
            // is the byte-identical wave-5 result.
            layout(fallback.width, fallback.height) { fallback.place(0, 0) }
        } else {
            // Per-line cross extent (the line box's width) and main extent
            // (the sum of its glyph advances).
            val lineW = plan.map { line -> line.maxOf { glyphPlaceables[it].width } }
            val lineH = plan.map { line -> line.sumOf { glyphPlaceables[it].height } }
            val totalW = lineW.sum()
            val totalH = lineH.maxOrNull() ?: 0
            val w = totalW.coerceIn(constraints.minWidth, constraints.maxWidth)
            val h = totalH.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(w, h) {
                // `vertical-rl` puts line 1 at the RIGHT edge and walks left;
                // `vertical-lr` starts at the left edge and walks right. Both
                // walk the plan in LOGICAL order — only the anchor differs.
                var x = if (stack == LineStack.RIGHT_TO_LEFT) totalW else 0
                plan.forEachIndexed { index, line ->
                    if (stack == LineStack.RIGHT_TO_LEFT) x -= lineW[index]
                    var y = 0
                    for (glyphIndex in line) {
                        val p = glyphPlaceables[glyphIndex]
                        // Centre the glyph across its line box — the vertical
                        // typesetting equivalent of a baseline-centred glyph
                        // in a horizontal line box. A no-op when every glyph
                        // in the line has the same advance (the CJK/fullwidth
                        // case, i.e. every upright run).
                        p.place(x + (lineW[index] - p.width) / 2, y)
                        y += p.height
                    }
                    if (stack == LineStack.LEFT_TO_RIGHT) x += lineW[index]
                }
            }
        }
    }
}
