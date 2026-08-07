package com.styleconverter.test.screenshot

// Wave 34 (lane H, H1) — the composed WPT canvas's DOCUMENT-ROOT row flow.
//
// ## The measured defect
// CSS2/abspos/static-inside-inline-block is four boxes of HTML:
//
//   <p>Test passes if there is a filled green square and no red.</p>
//   <div style="display:inline-block; width:100px; height:100px"></div>
//   <div style="display:inline-block; width:100px; height:100px;
//               background:red">
//     <div style="position:absolute; width:100px; height:100px;
//                 background:green"></div>
//   </div>
//   <div style="display:inline-block; width:100px; height:100px"></div>
//
// The three inline-blocks belong on ONE line box (CSS 2.1 §9.4.2) and the
// green abspos box paints at its static position inside the red one, so
// the browser-ref puts green at image (121, 88) — 16px frame + 100px + the
// 4.5px collapsed space, and 16 + 16 + a 40px two-line `<p>` + 16.
//
// Wave 33 (lane C) built the runtime side of exactly this packing
// (`InlineBlockAtom` + the wave-20 `InlineAtomFlow`) and then MEASURED that
// it could not reach this test: its three boxes are document ROOTS, and
// ComposedCaptureCanvas stacks roots in the HARNESS's own Column, which is
// not a runtime block box and never enters the block child loop. The frozen
// wave33-final scores were Android 0.8965 / iOS 0.9088 against a web that
// renders it natively at 0.9803.
//
// ## What this file is
// The Column's segment walk, and nothing else. Every RULE — which roots are
// atoms, where the runs are, how a run packs — comes from the runtime's
// public `InlineBlockAtom` root facade (`rootBox` / `rootSegments` /
// `rootRowPlan`), which is the same B1–B7 gate table and the same §9.4.2
// packer the nested lane uses. This file only:
//   • reads the per-root wire facts the facade needs (text, runs, and the
//     document's body-root line-height for B7), and
//   • adapts the pure plan to a Compose `Layout` — the exact measure/place
//     shape `runtimes/compose/.../layout/InlineFlowLayout.kt` uses for the
//     nested case, so a root row and a nested row cannot drift.
//
// ## Blast radius, enumerated before the change
// Replaying the facade over all 324 frozen wave33-final per-test IRs at the
// document-ROOT level yields exactly two tests:
//   • CSS2/abspos/static-inside-inline-block          — one 3-box run
//   • css-anchor-position/anchor-center-overflow-005  — two 4-box runs
//     (admitted only once wave-34 H2 made B5 box-sizing-aware; its boxes
//     are `box-sizing: border-box` with a 5px border)
// Every other composed section has NO root-level run, so `rootSegments`
// returns null there and ComposedCaptureCanvas executes its frozen
// `roots.forEachIndexed` loop verbatim. The dark-stage 327-pair corpus
// never reaches this canvas at all (it captures per COMPONENT through
// CaptureCanvas), so those baselines are byte-identical by construction.
//
// Twin: apps/ios-harness/StyleConverterTest/Screenshot/ComposedRootInlineFlow.swift

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.layout.InlineBlockAtom
import kotlin.math.roundToInt

/**
 * Per-root [InlineBlockAtom.RootBox] (null = "not an inline-block atom"),
 * index-aligned with [roots] — the input `InlineBlockAtom.rootSegments`
 * consumes.
 *
 * The three wire facts the facade needs:
 *  - `_text` / `runs` — B6: own line-box content moves an inline-block's
 *    baseline off its bottom margin edge (CSS 2.1 §10.8.1), so such a box
 *    leaves the lane;
 *  - the document's `body-root` `line-height` — B7: the packer's 16/4 strut
 *    pins are solved for the ref's INJECTED body line box (the `-lh-` term
 *    in the frozen ref-corpus id), and an author override invalidates them.
 *    The body-root lookup is the same `role == "body-root"` read
 *    `resolveComposedCanvasBackground` / `resolveComposedCanvasPadding`
 *    already do, so the three resolvers cannot disagree about which root
 *    represents the document body.
 *
 * Pure over the decoded IR — unit-pinned in ComposedRootInlineFlowTest.
 */
internal fun composedRootInlineBoxes(roots: List<IRComponent>): List<InlineBlockAtom.RootBox?> {
    // B7's container read: does the document's body declare its own
    // line-height? Computed once — it is a property of the DOCUMENT, not
    // of any individual root.
    val bodyDeclaresLineHeight = roots
        .firstOrNull { it.role == "body-root" }
        ?.properties?.any { it.type == "LineHeight" } == true
    return roots.map { root ->
        InlineBlockAtom.rootBox(
            properties = root.properties,
            // B6 — the component's own text is a statically visible line box.
            hasOwnText = !root._text.isNullOrEmpty(),
            // B6 — so is an ordered inline-run list (wave-32 lane R).
            hasOwnRuns = !root.runs.isNullOrEmpty(),
            containerDeclaresLineHeight = bodyDeclaresLineHeight,
        )
    }
}

/**
 * Lay one run of inline-block ROOTS out as §9.4.2 rows.
 *
 * Byte-for-byte the measure/place shape of the runtime's
 * `InlineFlowLayout` (wave-20 lane W3), with the one simplification the
 * root family allows: every member's border box is DEFINITE (B2 admitted it
 * on exactly that condition), so both axes are exact constraints and no
 * member ever measures free.
 *
 *  - measurement: `boxes[i]` becomes an exact `Constraints`, so the root
 *    paints inside precisely the box the packer placed for it;
 *  - packing: `InlineBlockAtom.rootRowPlan` at the incoming max width —
 *    the Column's CONTENT width, 358dp on the composed canvas, which is
 *    what makes 3×100 + 2×4.5 = 309 fit on one row and a 4th wrap;
 *  - reporting: the run's own extent, coerced into the incoming envelope,
 *    so the next root stacks below the LAST row (and overflowing ink still
 *    draws unclipped, the same contract the nested adapter pins).
 *
 * CSS px == dp at the capture density (the composed canvas is 390dp wide
 * and its PNG is 390px wide), and the `.dp` round-trips here are the same
 * ones InlineFlowLayout performs, so the two adapters agree at any density.
 */
@Composable
internal fun ComposedRootInlineRow(
    boxes: List<InlineBlockAtom.RootBox>,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier) { measurables, constraints ->
        // Exact per-member constraints from the shared facade's boxes.
        val placeables = measurables.mapIndexed { i, m ->
            // Index-aligned by construction (the caller builds `boxes` and
            // the content lambda from the SAME segment indices); a drift is
            // a harness bug, so degrade to a free measure rather than crash.
            val b = boxes.getOrNull(i)
            val wPx = b?.widthPx?.dp?.roundToPx()
            val hPx = b?.heightPx?.dp?.roundToPx()
            m.measure(
                Constraints(
                    minWidth = wPx ?: 0,
                    maxWidth = wPx ?: Constraints.Infinity,
                    minHeight = hPx ?: 0,
                    maxHeight = hPx ?: Constraints.Infinity,
                )
            )
        }
        // The pure plan over the MEASURED geometry, in device px.
        val plan = InlineBlockAtom.rootRowPlan(
            widthsPx = placeables.map { it.width.toDouble() },
            heightsPx = placeables.map { it.height.toDouble() },
            // The Column's content width drives the wrap; an unbounded
            // container simply never wraps.
            availableWidthPx =
                if (constraints.hasBoundedWidth) constraints.maxWidth.toDouble()
                else Double.POSITIVE_INFINITY,
            // The collapsed source white-space between inline siblings,
            // converted exactly like InlineFlowLayout converts it.
            gapPx = InlineBlockAtom.ROOT_ATOM_GAP_PX.dp.toPx().toDouble(),
        )
        // Report the flow's own extent inside the incoming envelope.
        val reportedW = plan.widthPx.roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val reportedH = plan.heightPx.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(reportedW, reportedH) {
            // Whole-px placement (roundToInt, ties away from zero — the
            // shared rounding pin across the wave-19/20 layout adapters).
            // 104.5 → 105 is what lands the target test's green box at the
            // ref's image x = 16 + 105 = 121.
            placeables.forEachIndexed { i, p ->
                p.place(plan.xPx[i].roundToInt(), plan.yPx[i].roundToInt())
            }
        }
    }
}
