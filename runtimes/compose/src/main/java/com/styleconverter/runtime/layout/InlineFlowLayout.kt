package com.styleconverter.runtime.layout

// InlineFlowLayout — the Compose adapter over the pure InlineAtomFlow
// twins (wave-20 lane W3). Renders ONE inline atom run (≥2 consecutive
// inline-level UA widgets / text-only anchors, InlineAtomFlow.segment)
// as wrapped rows per CSS 2.1 §9.4.2, inside the parent's block Column.
// Composed-WPT capture only — the caller (ComponentRenderer's block
// child loop) gates on LocalWptCaptureMode, so the dark-stage 327
// corpus never enters this layout.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Wave-38 lane N1 — "this composition is a MEMBER of a packed inline atom
 * run". [InlineFlowLayout] provides `true` around its own content, which is
 * exactly the run members (the layout is instantiated per run segment), so
 * the UA-widget mount hook can tell the two placement regimes apart:
 *  • run member → this layout already builds the §10.8 line box and places
 *    the atom against the row baseline, so the widget must paint flush in
 *    its promised box (adding lead here would shift the ink out of the
 *    plan's slot);
 *  • anything else → the widget is a lone inline-level box in a block and
 *    gets no line box at all without [UAWidgetBlockLine].
 * `compositionLocalOf` (not static): the value differs per subtree and the
 * default `false` keeps every non-run composition byte-identical.
 * Twin: the SwiftUI `inlineAtomRunMember` EnvironmentValue.
 */
internal val LocalInlineAtomRunMember = compositionLocalOf { false }

/**
 * Lay an inline atom run out as wrapped rows.
 *
 * Measurement (P18): atoms whose kind has FIXED UA intrinsic sizes in
 * the shared UAWidgetIntrinsics table are measured with those exact
 * constraints — the widget replica (lane W2) paints INSIDE the same box
 * the table promises, so placement and painting cannot drift. Atoms
 * without a fixed axis (text anchors, label-driven buttons/selects)
 * measure UNBOUNDED on that axis and contribute their content size —
 * the §10.3.5-ish shrink-to-fit the ref applies to label-sized
 * controls.
 *
 * Packing/baselines (P16/P17): the pure InlineAtomFlow.layout packs at
 * the incoming max width — the container's CONTENT width (the css-ui
 * refs declare `width: 500px`, which arrives here as the bounded
 * constraint), reproducing the ref's row wraps; unbounded containers
 * simply never wrap.
 *
 * [atoms] carries the per-member AtomSpec (index-aligned with the run's
 * composables — the caller builds both from the same segment indices).
 */
@Composable
internal fun InlineFlowLayout(
    atoms: List<UAWidgetIntrinsics.AtomSpec>,
    content: @Composable () -> Unit,
) {
    Layout(
        // Wave-38 lane N1 — mark the run members (see
        // [LocalInlineAtomRunMember]). CompositionLocalProvider emits no
        // layout node, so the measurable list — and every index the plan
        // below aligns to it — is unchanged.
        content = { CompositionLocalProvider(LocalInlineAtomRunMember provides true) { content() } },
        modifier = Modifier,
    ) { measurables, constraints ->
        // P18 — per-atom measurement under the table's fixed axes. CSS
        // px == dp at the capture density (the FloatRowLayout precedent).
        val placeables = measurables.mapIndexed { i, m ->
            // The spec for this member (index-aligned by construction).
            val spec = atoms.getOrNull(i)
            // Fixed axes become exact constraints; free axes unbounded.
            val wPx = spec?.fixedWpx?.dp?.roundToPx()
            val hPx = spec?.fixedHpx?.dp?.roundToPx()
            m.measure(
                Constraints(
                    minWidth = wPx ?: 0,
                    maxWidth = wPx ?: Constraints.Infinity,
                    minHeight = hPx ?: 0,
                    maxHeight = hPx ?: Constraints.Infinity,
                )
            )
        }
        // P16/P17 — the pure flow plan over the resolved geometry. The
        // pure math runs in CSS px; measured px == dp px at capture
        // density, so no conversion beyond the table's dp round-trip.
        val plan = InlineAtomFlow.layout(
            widths = placeables.map { it.width.toDouble() },
            heights = placeables.map { it.height.toDouble() },
            // Baseline descents come from the shared table (P17). Index-
            // aligned against the MEASURED list defensively — a spec/
            // measurable count drift (a renderer bug, not layout state)
            // degrades to bottom-aligned zero-margin atoms rather than
            // crashing the capture.
            descents = placeables.indices.map { atoms.getOrNull(it)?.descentPx ?: 0.0 },
            // UA margins (checkbox/radio) enter the margin-box packing.
            marginStarts = placeables.indices.map { atoms.getOrNull(it)?.marginStartPx ?: 0.0 },
            marginEnds = placeables.indices.map { atoms.getOrNull(it)?.marginEndPx ?: 0.0 },
            // The container's content width drives the wrap (bounded on
            // the css-ui refs' 500px container; ∞ → single row).
            availableWidth =
                if (constraints.hasBoundedWidth) constraints.maxWidth.toDouble()
                else Double.POSITIVE_INFINITY,
            // The collapsed source white-space between inline siblings.
            gapPx = UAWidgetIntrinsics.ATOM_GAP_PX.dp.toPx().toDouble(),
            // P17b (fix 4) — the §10.8.1 line-box strut: rows of short
            // atoms (checkbox row, the lone progress bar) still get the
            // block's 16/4 text metrics (wave-22 re-solve), matching the
            // ref's row pitches.
            strutAscentPx = UAWidgetIntrinsics.STRUT_ASCENT_PX,
            strutDescentPx = UAWidgetIntrinsics.STRUT_DESCENT_PX,
        )
        // Report the flow's own extent, coerced into the envelope (the
        // absposReportedAxis discipline): following block siblings stack
        // below the last row; overflowing ink still draws unclipped.
        val reportedW = plan.width.roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val reportedH = plan.height.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(reportedW, reportedH) {
            // Whole-px placement (roundToInt, ties half-up — the shared
            // rounding pin across the wave-19/20 layout adapters).
            placeables.forEachIndexed { i, p ->
                p.place(plan.x[i].roundToInt(), plan.y[i].roundToInt())
            }
        }
    }
}
