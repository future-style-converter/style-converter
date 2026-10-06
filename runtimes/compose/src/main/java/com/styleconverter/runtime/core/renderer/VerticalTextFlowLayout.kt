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
// it. When `VerticalTextFlow.uprightColumnIndices` declines — no budget at
// all, a degenerate advance, a >64-line plan — this composable places the
// `rotatedRun` slot instead and the frame is exactly what the frozen
// renderer drew. Nothing is guessed.
//
// Wave 52 lane L8 (M-C): an UNBOUNDED height constraint is no longer "no
// budget". css-writing-modes-4 §7.3.1 gives an orthogonal flow with an
// indefinite available inline size a definite one — the nearest ancestor's
// definite block size, else the initial containing block — and the composed
// capture publishes both (`LocalContainingBlock`, `LocalComposedViewport`).
// The fallback is WPT-capture-only ([uprightFallbackBudgetPx], gated on
// `LocalWptCaptureMode` exactly as the Swift twin gates on
// `\.wptCaptureMode`): the containing-block channel is published on EVERY
// surface, so outside capture the gate — not the channel's absence — keeps
// the historical decline byte for byte. Measured before the change: wave51-fix
// ch-units-vrl-005..008's orange upright `00000` drew as the ROTATED run
// (55×72 with its 4dp padding) where the ref stacks five upright glyphs in
// one 120×120 column; direction-upright-001/002's cells the same way.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
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
    // Wave 48 (lane W1): the measure moved VERBATIM into
    // rotatedRunMeasurePolicy so the policy can carry EXPLICIT transposed
    // intrinsics. The trailing-lambda overload inherited the DEFAULT
    // intrinsics, whose fake placeables substitute LargeDimension (32767)
    // for an unbounded axis — and this layout's axis swap then reported
    // that sentinel as real perpendicular geometry (each rotated <td>
    // answered a 32769 max-content width, the table summed 98311, the
    // row's capped §17.5.3 height became 8190, and every vertical root
    // composed 8264 px tall — the direction-upright-002 unmeasured cell).
    // Full measured chain + the transposition table: VerticalRunIntrinsics.
    Layout(
        modifier = modifier,
        content = run,
        measurePolicy = remember(rotationDegrees) {
            rotatedRunMeasurePolicy(rotationDegrees)
        },
    )
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
    // Wave 52 lane L8 (M-C) — css-writing-modes-4 §7.3.1: when the measure's
    // height constraint is UNBOUNDED (the block axis of this orthogonal flow
    // is `height: auto` all the way up — wave51-fix ch-units-vrl-005..008's
    // orange `width: 5ch` div, direction-upright-001/002's cells), the
    // available inline size is the nearest ancestor's DEFINITE block size,
    // else the initial containing block's extent. Both are already
    // published to this subtree: the containing-block channel
    // (`LocalContainingBlock.heightPx`, null when the ancestor is `auto` —
    // published on EVERY surface, product included) and the composed
    // capture's viewport (`LocalComposedViewport.heightPx` = the ref's
    // 568 px ICB, null on every non-composed path). Both are read
    // unconditionally (stable composition) and gated by
    // [uprightFallbackBudgetPx] on `LocalWptCaptureMode`, the Swift twin's
    // gate, so the app and the dark stage keep the historical decline.
    val containingBlockHeightPx = com.styleconverter.runtime.core.variables
        .LocalContainingBlock.current.heightPx?.toDouble()
    val icbBlockExtentPx = LocalComposedViewport.current?.heightPx?.toDouble()
    val fallbackBudgetPx = uprightFallbackBudgetPx(
        LocalWptCaptureMode.current, containingBlockHeightPx, icbBlockExtentPx)
    // Wave 48 (lane W1): the measure moved VERBATIM into
    // uprightFlowMeasurePolicy so the policy carries EXPLICIT glyph-based
    // intrinsics — the default lambda-replay intrinsics were poisoned by
    // the same LargeDimension (32767) fakes as the rotated run's (this
    // flow's slot 0 IS a rotated run, and each glyph fake answered 32767
    // on its unbounded axis). See VerticalRunIntrinsics for the measured
    // chain and the css-sizing-3 §5.1 min/max-content model the overrides
    // implement. The decline breadcrumb stays here (the composable owns
    // the log channel; the policy stays log-free and JVM-testable).
    Layout(
        modifier = modifier,
        content = {
            rotatedRun()                          // slot 0 — decline fallback
            glyphs.forEach { uprightGlyph(it) }   // slots 1 … n
        },
        // The policy is keyed on the fallback too: a viewport or containing
        // block that changes between compositions must re-plan, not replay.
        measurePolicy = remember(glyphs, stack, fallbackBudgetPx) {
            uprightFlowMeasurePolicy(glyphs, stack, onDecline = {
                // No silent fallthrough: the GATE already said this run is
                // upright, so a decline is a real, named gap and not a
                // routine "not our case". Logged once per process — the
                // Swift twin logs the same key via PropertyTracker.logOnce.
                // Wave 52 L8: with the §7.3.1 fallback in place a decline
                // means no budget at all (outside WPT capture, or a glyphless
                // / zero-advance run), so the breadcrumb names both.
                if (uprightDeclineLogged.compareAndSet(false, true)) {
                    android.util.Log.i(
                        "ComponentRenderer",
                        "writing-mode:upright-vertical-budget — upright vertical run " +
                            "declined: no finite block-axis budget (constraint unbounded and " +
                            "§7.3.1 fallback ${fallbackBudgetPx ?: "absent — not a WPT capture, or no " +
                                "definite containing block / composed viewport"}) " +
                            "or a glyphless/zero-advance run; run kept on the rotated path",
                    )
                }
            }, fallbackBudgetPx = fallbackBudgetPx)
        },
    )
}

/**
 * Wave 52 lane L8 (M-C) — the css-writing-modes-4 §7.3.1 fallback budget an
 * upright run plans against when its height constraint is unbounded, or null
 * (keep the decline). Twin of the Swift `VerticalUprightGate.budgetPx(style:
 * viewport:wptCaptureMode:)`: FALSE capture mode answers null, because the
 * app's viewport is not the ref's ICB and must not become a wrap budget, and
 * because `LocalContainingBlock` is published on every surface (so the
 * channel's presence cannot be the gate). Pure + internal for the JVM pin.
 */
internal fun uprightFallbackBudgetPx(
    wptCaptureMode: Boolean,
    containingBlockHeightPx: Double?,
    icbBlockExtentPx: Double?,
): Double? =
    // Outside WPT capture: no fallback — the pre-wave-52 decline, byte for byte.
    if (!wptCaptureMode) null
    // Capture: the nearest definite ancestor block size, else the ICB.
    else com.styleconverter.runtime.typography.text.VerticalInlineAxis
        .orthogonalBudget(containingBlockHeightPx, icbBlockExtentPx)
