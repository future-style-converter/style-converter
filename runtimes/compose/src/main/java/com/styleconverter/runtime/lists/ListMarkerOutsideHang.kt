package com.styleconverter.runtime.lists

// The `list-style-position: outside` marker HANG — wave 52, lane L6 (T5),
// the deferred "B-RC3 part 3" both native renderers name in their marker
// branch comments. BYTE-PARALLEL TWIN of runtimes/swiftui/Sources/
// StyleConverterRuntime/StyleEngine/lists/ListMarkerOutsideHang.swift.
//
// ## The defect (measured on wave51-fix, pngjs over the run PNGs)
// css-counter-styles/counter-suffix `ios f 0.9285` / `android f 0.9051`:
// rows 1–8 start at x 64–65 (marker `1.` at x 64–73, item text at
// 80–103) where the frozen ref's row 1 ink runs x 46–58 (the marker,
// hanging in the `<ol>`'s 3em = 48 px padding) and x 64–87 (the text).
// css-lists/counter-list-item-2: ref x 38–61, natives x 56–77. In every
// case the item is displaced by exactly `markerWidth + gap` (+18 px),
// because ComponentRenderer.RenderListItemMarker composes
// `Row { marker; item }` — the marker is a SIBLING that takes inline
// space the CSS box model never gives it.
//
// ## The spec
// css-lists-3 §3.5: for `list-style-position: outside` the ::marker box
// is "positioned outside the principal block box, before its start edge
// … the marker box's inline-start edge is placed at the item's border-box
// inline-start edge minus the marker's width (and the UA marker padding)";
// the item's own content edge does not move. So the item is laid out
// EXACTLY as if it had no marker, and the marker is painted into the
// item's margin area (its parent's padding, in the corpus) to the left of
// the border box — the reported size of the pair is the ITEM's size only.
//
// ## Why a custom Layout and not an offset on the Row
// A Row cannot give a child negative inline extent; `Modifier.offset` on
// the marker would move its ink but not the space it reserves, so the
// item would still start 18 px late. `Layout` measures both, reports the
// item's size and places the marker outside the bounds — the one shape
// that satisfies "content edge unchanged" and "marker visible" at once.
//
// ## RTL (BACKLOG (c⁴): RTL markers are BLOCKED UPSTREAM)
// css-lists-3 §3.5 hangs an `outside` marker on the inline-END side under
// `direction: rtl`. `Placeable.place` is NOT mirrored by Compose (unlike
// `placeRelative`), so the mirror is spelled here from the ambient
// LayoutDirection. The corpus's RTL rows still lack markers for an
// UPSTREAM reason (no `meta.markerText` reaches them), so this branch is
// pinned but does not move a cell this wave — stated, not hidden.
//
// Staged as a DEVICE A/B: the renderer seam that swaps the Row for this
// Layout is `tools/titan/results/wave52-counters-and-lists/seam-1.patch`,
// applied by the orchestrator against the closing gate with the installed
// `base.apk` sha1 recorded — never blind-lifted. 100 `<li>` items in 19
// tests carry an outside marker (census T5 in the lane note); several passing
// natives sit within 0.003 of 0.95.

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection

object ListMarkerOutsideHang {

    /**
     * Where the two boxes go, in the pair's own coordinate space (origin =
     * the item's border-box origin). [width] × [height] is the size the
     * pair REPORTS — the item's alone, by §3.5.
     */
    data class Placement(val markerX: Int, val markerY: Int, val width: Int, val height: Int)

    /**
     * The pure geometry, pinned in ListMarkerOutsideHangTest (Compose's
     * JVM stage cannot lay out; iOS's twin pins the identical arithmetic).
     *
     * @param markerBaseline the marker's FirstBaseline, null if it exposes
     *   none; [itemBaseline] likewise for the item's principal box.
     * @param gapPx the inline gap between marker and item — the caller
     *   hands in [ListMarkerRow.gapDp] resolved to px, so the two
     *   placements share ONE gap constant.
     * @param alignsByBaseline the row's per-item decision
     *   ([ListMarkerRow.alignsByBaseline]); false ⇒ both boxes stack by
     *   their tops (the baseline-less items and the snapped-grid case).
     * @param rtl hang on the inline-END side instead (css-lists-3 §3.5
     *   under `direction: rtl`).
     * @param itemIsEmpty [ListMarkerEmptyItem.isEmpty]: the item has no
     *   content, so the marker's line is the item's only line box and the
     *   pair is at least the marker's height (header of that file).
     */
    fun place(
        markerWidth: Int, markerHeight: Int, markerBaseline: Int?,
        itemWidth: Int, itemHeight: Int, itemBaseline: Int?,
        gapPx: Int, alignsByBaseline: Boolean, rtl: Boolean = false,
        itemIsEmpty: Boolean = false
    ): Placement {
        // Inline: the marker's END edge sits `gap` before the item's START
        // edge — `markerX + markerWidth + gap == 0` is the pinned identity.
        // Under RTL the mirror image: the marker's START edge sits `gap`
        // after the item's END edge.
        val markerX = if (rtl) itemWidth + gapPx else -(markerWidth + gapPx)
        // Block: share the first line's baseline when the row aligns by
        // baseline and BOTH boxes expose one (css-lists-3 §3.5 — the marker
        // is aligned with the item's first line box); otherwise top-align,
        // exactly the Row's `Alignment.Top` fallback.
        val markerY = if (alignsByBaseline && markerBaseline != null && itemBaseline != null)
            itemBaseline - markerBaseline else 0
        // The pair reports the ITEM's size only: the marker adds no width
        // (that was the whole defect) and no height (a marker taller than
        // a definite-height item overflows, css-sizing-3 §5.1) — except for
        // an EMPTY item, whose one line box is the marker's (CSS 2.1 §10.6.3).
        return Placement(markerX, markerY, itemWidth,
            if (itemIsEmpty) maxOf(itemHeight, markerY + markerHeight) else itemHeight)
    }

    /**
     * The composable: `[marker]` hung outside `[item]`. Each slot is
     * wrapped in a Box so the measure policy sees exactly two measurables
     * whatever the slot emits; Box propagates its child's FirstBaseline,
     * so the baseline read below sees the real text line.
     */
    @Composable
    fun Hang(
        gapPx: Int,
        alignsByBaseline: Boolean,
        // [ListMarkerEmptyItem.isEmpty] of the item — see [place].
        itemIsEmpty: Boolean = false,
        modifier: Modifier = Modifier,
        marker: @Composable () -> Unit,
        item: @Composable () -> Unit
    ) {
        Layout(content = { Box { marker() }; Box { item() } }, modifier = modifier) { measurables, constraints ->
            // The marker is shrink-to-fit inline content sized by its glyphs
            // (css-lists-3 §3.5) — measured UNBOUNDED, exactly like
            // ListMarkerRow.insideMarkerOverlay does for the inside branch.
            val markerP = measurables[0].measure(Constraints())
            // The item takes the constraints the pair received: it is laid
            // out as if the marker did not exist.
            val itemP = measurables[1].measure(constraints)
            // Baselines are optional — Unspecified reads as "none".
            val mb = markerP[FirstBaseline].takeIf { it != AlignmentLine.Unspecified }
            val ib = itemP[FirstBaseline].takeIf { it != AlignmentLine.Unspecified }
            val p = place(
                markerP.width, markerP.height, mb, itemP.width, itemP.height, ib,
                gapPx, alignsByBaseline, rtl = layoutDirection == LayoutDirection.Rtl,
                itemIsEmpty = itemIsEmpty
            )
            // Report the item's size; the item's own FirstBaseline propagates
            // to ancestors automatically (it is placed at the origin), so an
            // outer baseline-aligned row still sees the right line.
            layout(p.width, p.height) {
                itemP.place(0, 0)
                // Absolute `place`, not `placeRelative`: the RTL mirror is
                // already in `p.markerX` (see the header).
                markerP.place(p.markerX, p.markerY)
            }
        }
    }
}
