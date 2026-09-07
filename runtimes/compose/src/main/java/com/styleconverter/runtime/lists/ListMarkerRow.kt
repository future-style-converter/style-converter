package com.styleconverter.runtime.lists

// Compose-side geometry decisions for the synthesized `::marker` box —
// wave 28, lane MC. BYTE-PARALLEL TWIN of
// runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/
// ListMarkerRow.swift: same three decisions, same names, same spec
// citations, so the two natives cannot drift on marker placement.
//
// ## What wave 27 left broken (measured on the LIVE wave27-final section)
// tools/titan/runs/wave27-final/sections/css-counter-styles/ —
// `meta.markerText` IS consumed on both natives (the baked digits and the
// `<ol start>` ordinals paint correctly), yet only 3/12 Android and 5/12
// iOS tests score. Measuring the ink columns of
// {screenshots,android-screenshots,ios-screenshots}/wpt__css-counter-
// styles__arabic-indic__css3-counter-styles-101.png gives:
//
//   platform | marker x | ref-glyph x | row pitch
//   web      | 217–233  | 244–253     | 32px
//   iOS      | 217–235  | 264–280     | 31px
//   Android  | 218–231  | 268–280     | 43px
//
// Two independent defects, both in the marker ROW and neither in the
// baked-string plumbing:
//
// 1. HORIZONTAL (both natives). The row prepends the marker as a SIBLING
//    of the item, so the item's principal box starts at
//    `markerWidth + gap` instead of at the row origin. Every one of these
//    items holds a single absolutely-positioned glyph at `left: 39.72px`,
//    and css-position-3 §2.1 anchors that to the ITEM's padding box — so
//    the reference column inherits the whole displacement (+~24px above,
//    and up to +70px on the wide `start:10000` markers of -102/-007/
//    -117/-159, which are exactly the four rows scoring 0.60–0.70).
//    css-lists-3 §3.5 is explicit that a `list-style-position: inside`
//    marker is the item's FIRST INLINE BOX — it lives INSIDE the
//    principal box and cannot move it. See [rendersInsideOverlay].
//
// 2. VERTICAL (Android only — iOS repaired its half in wave 27 with
//    ListMarkerRow.rowAlignment). Compose's marker row applies
//    `Modifier.alignByBaseline()` UNCONDITIONALLY. Its comment claims
//    Compose degrades an absent `FirstBaseline` to the cross-axis origin
//    "i.e. exactly the Alignment.Top this replaces". That is wrong about
//    the row's EXTENT: RowColumnImpl treats an unspecified alignment line
//    as offset 0, so the row's height becomes
//    `max(baselineOffset) + max(height − baselineOffset)` — the marker's
//    whole ascent is added ABOVE an item that has no baseline of its own,
//    which is precisely the 43px-vs-32px pitch and the 8px-late first row
//    in the table above. See [alignsByBaseline].

import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.renderer.ComponentRenderer

object ListMarkerRow {

    /**
     * Inline gap between the marker box and the item's principal box.
     *
     * 4dp, the pre-wave-24 constant — and now the ONLY gap: the renderer
     * used to paint `"$marker "` (a trailing U+0020 at the item's font
     * size, ~7dp at the 25px these tests inherit) AND this padding, while
     * the iOS twin painted the bare string with a 4pt HStack spacing. That
     * silent ~7dp made the two natives disagree on every marker row. The
     * real value is UA-defined and per-counter-style (css-lists-3 §3.2's
     * marker padding) — still deferred, still shared with iOS's `gapPt`.
     */
    val gapDp = 4.dp

    /**
     * Should the `[marker, item]` row align its two children on a shared
     * text baseline? Compose spelling of iOS's
     * `ListMarkerRow.rowAlignment(itemExposesTextBaseline:)`.
     *
     * - `true` when the item CAN expose one: the marker is the item's
     *   first inline box and shares the line's baseline (css-lists-3
     *   §3.2). The pre-wave-27 behaviour, kept verbatim for every item it
     *   was ever correct for.
     * - `false` otherwise → plain `Alignment.Top` with NO
     *   `alignByBaseline()` claim on either child. With no baseline to
     *   share, Compose's row math hangs the marker's ascent above the
     *   item and grows the row by it (defect 2 in the file header);
     *   top-alignment starts the marker at the item's content-box origin,
     *   so a marker no taller than the item cannot grow it (css-sizing-3
     *   §5.1: a definite `height` IS the used height — larger content
     *   overflows rather than stretching the box).
     *
     * ## Wave 47 (lane Z5) — [sharesSnappedLineGrid] vetoes the claim
     *
     * When BOTH children are snapped onto the SAME resolved CSS line box
     * ([ListMarkerLineBox.snap] on the marker, `composedLineBoxSnap` on
     * the item's run — one `resolve`, one grid), baseline alignment is
     * not just redundant, it is the +1px/row divergence. MEASURED on
     * emulator-5690 (wave-47 Z5 probe, armenian-006/007 + bengali-117,
     * every row identical):
     *
     *   marker Text  post-snap  h=31  claimed baseline 25
     *   item wrapper post-snap  h=31  claimed baseline 24
     *   row (baseline-aligned)  h=32  — every row, vs the ref's 31.25 grid
     *
     * Both Texts lay out the IDENTICAL paragraph (ceil(31.25)=32 natural,
     * LineHeightStyleSpan Center baseline 25 — 1px below Chromium's
     * floor(ascent + half-leading) = 24) and both snaps preserve it — the
     * ±1 is not typographic at all. It is a CHANNEL split: the Row reads
     * the marker's baseline straight off the Text's measure result (25),
     * but the item's Text sits under wrapper Boxes, and Compose folds the
     * wave-39 FIX-4 half-leading graphicsLayer translation (−1, the
     * draw-time ink correction) into the alignment line those ancestors
     * propagate (24). PROVEN by A/B: zeroing FIX-4's `translationY` alone
     * flipped every wrapper claim 24 → 25. Aligning on two claims that
     * disagree about the same line makes the Row
     * `max(25,24) + max(31−25, 31−24) = 32` on every row — a constant
     * 32px pitch against the ref's alternating 31/32 (31.25px box), i.e.
     * +0.75px/row accumulating until the last ink row falls off the
     * capture — AND offsets the item +1 while FIX-4 pulls only the
     * marker's ink −1, the "identical rasters, item ink ±1" the wave-46
     * gate measured.
     *
     * The CSS model has no second reconciliation to make: marker and item
     * share ONE line box whose position is the block grid's (css-inline-3
     * §4.2 — the line box stacks in the block flow; inline boxes sit
     * INSIDE it by the half-leading model, which here is the two FIX-4
     * draw translations, both landing ink on the model baseline 24). So
     * when the shared grid exists, the row stacks the two equal-grid
     * boxes by their TOPS: the row's height is the snapped line box
     * (the accumulator's 31/32/31/31… — the ref's own sub-pixel
     * stacking), and both ink runs land on one baseline by construction.
     * Baseline claims stay authoritative everywhere else — declared
     * `line-height: normal` (no CSS box: the faces' real metrics ARE the
     * line), every non-composed capture path, and the whole dark stage,
     * where [sharesSnappedLineGrid] is false and this function is
     * byte-identical to its wave-28 self.
     *
     * KNOWN LIMIT, stated not silent: an item whose FIRST LINE sits below
     * its box top (own top padding/margin) would need the baseline claim
     * even under the grid; no marker row in the corpus has one (the
     * counter-styles/css-lists items are bare text), and the honest fix
     * there is the still-deferred B-RC3 part-3 custom layout.
     *
     * @param itemExposesTextBaseline does the item's principal box carry
     *   a first text baseline at all (see [itemExposesTextBaseline])?
     * @param sharesSnappedLineGrid are BOTH children snapped onto one
     *   resolved CSS line box (composed WPT capture with a resolvable
     *   box — the exact gate [ListMarkerLineBox.snap] is active under)?
     *   Defaults to false so every pre-wave-47 caller and test is
     *   byte-identical.
     */
    fun alignsByBaseline(
        itemExposesTextBaseline: Boolean,
        sharesSnappedLineGrid: Boolean = false
    ): Boolean =
        itemExposesTextBaseline && !sharesSnappedLineGrid

    /**
     * Does this list item render any IN-FLOW text, i.e. will its box carry
     * a first text baseline for the row to align against? Twin of iOS's
     * `itemExposesTextBaseline(_:)`, over the same IR.
     *
     * Out-of-flow descendants are excluded deliberately and are the whole
     * point of the repair: an absolutely positioned run paints through the
     * parent's positioned OVERLAY (css-position-3 §2.1 takes it out of
     * flow), so it contributes neither height nor a baseline to the item's
     * principal box — which is exactly the shape the counter-style bidi
     * bake produces for every `<li>` in this section.
     *
     * Recursion is over in-flow children only, so it terminates on the
     * same finite subtree the flow container actually lays out.
     */
    fun itemExposesTextBaseline(item: IRComponent): Boolean {
        // The item's own text run renders as a leading in-flow label.
        if (!item._text.isNullOrEmpty()) return true
        // Otherwise any in-flow descendant's text supplies the baseline.
        return item.children.orEmpty().any { child ->
            !ComponentRenderer.isOutOfFlowChild(child.properties) &&
                itemExposesTextBaseline(child)
        }
    }

    /**
     * Must this item's marker be painted INSIDE the item's own box —
     * i.e. without a row that displaces the box — rather than prepended
     * as a sibling? Twin of iOS's `rendersInsideOverlay(position:…)`.
     *
     * Both gates are load-bearing:
     *
     * - `position == INSIDE` only. css-lists-3 §3.5 puts an `inside`
     *   marker in the item's content, but an `outside` marker in the
     *   item's MARGIN area, to the left of the border box — drawing that
     *   one at the content-box origin would move it right by its own
     *   width. `outside` therefore keeps the row (its own displacement of
     *   the item is the still-deferred B-RC3 part-3 geometry, called out
     *   at the renderer's marker branch — not silently ignored here).
     *   `null` keeps the row too: unknown position ⇒ unchanged
     *   behaviour. REACHABILITY (measured, wave-28 skeptic pass —
     *   the earlier note here named the wrong case):
     *   [ListStyleExtractor.resolveMarkerConfig] returns null on exactly
     *   ONE condition, `uaMarkerDefault(parentTag) == null`, i.e. the
     *   PARENT is not a list container — never because of the child. On
     *   Compose the renderer only reaches
     *   `ComponentRenderer.RenderListItemMarker` when `isListParent` is
     *   already true, so null is unreachable here and this arm is purely
     *   defensive; the iOS twin's marker branch is gated on a non-empty
     *   marker string INSTEAD of on the parent tag, so a producer that
     *   bakes `meta.markerText` under a non-list parent does reach it
     *   there. In particular an UNTAGGED child under a real `<ol>`
     *   resolves a NON-null config (the container's own
     *   `list-style-position`), so it is not this arm's case at all — on
     *   iOS such a child, if it exposes no in-flow text, takes the
     *   overlay.
     *
     * - the item must expose NO in-flow text. An `inside` marker on an
     *   item that HAS text must push that text right along the line, and
     *   the overlay does not — it would paint the marker ON TOP of the
     *   first word. Those items keep the row, whose leading-box shape is
     *   the right answer for the text even though it still displaces the
     *   box. KNOWN GAP, deliberately narrow: it is invisible unless the
     *   item also anchors an out-of-flow descendant, and no such shape
     *   exists in the corpus.
     */
    fun rendersInsideOverlay(
        position: ListStylePosition?,
        itemExposesTextBaseline: Boolean
    ): Boolean =
        position == ListStylePosition.INSIDE && !itemExposesTextBaseline

    /**
     * Measure-and-place modifier for the overlaid marker: measure it
     * UNBOUNDED (shrink-to-fit — the ::marker box is inline-level content
     * sized by its glyphs, css-lists-3 §3.5; iOS spells the same rule
     * `.fixedSize()`), then report a ZERO size so the enclosing Box sizes
     * itself from the item alone and the marker can neither widen nor
     * heighten the item's principal box.
     *
     * Placed at (0, 0) = the item's border-box origin. KNOWN
     * APPROXIMATION: css-lists-3 §3.5 wants the item's CONTENT-box origin,
     * so an item with its own padding/border would offset the marker by
     * that much. Every `<li>` in the counter-styles corpus declares
     * neither, and closing it needs the item's resolved box metrics at
     * this call site.
     */
    fun insideMarkerOverlay(): Modifier = Modifier.layout { measurable, _ ->
        val placeable = measurable.measure(Constraints())
        layout(0, 0) { placeable.place(0, 0) }
    }
}

/**
 * The baseline claim for one child of the marker row, or a no-op when
 * [ListMarkerRow.alignsByBaseline] said not to align. Top-level rather
 * than a member of [ListMarkerRow] because it needs the caller's
 * `RowScope` receiver, which only the Row content lambda has.
 *
 * BOTH children must take it: `alignByBaseline` is a RowScope parent-data
 * claim, and a row with only ONE baseline-aligned child has nothing to
 * align it against, so marking the marker alone would be an inert no-op.
 */
fun RowScope.markerBaselineClaim(aligns: Boolean): Modifier =
    if (aligns) Modifier.alignByBaseline() else Modifier
