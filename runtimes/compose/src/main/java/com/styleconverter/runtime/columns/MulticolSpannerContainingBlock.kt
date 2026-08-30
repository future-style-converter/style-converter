package com.styleconverter.runtime.columns

// Wave 49 (lane A7) — the CONTAINING-BLOCK CHAIN across a column spanner.
//
// ## The measured defect
// `tools/titan/runs/wave48-final/sections/css-multicol/report/images/`
// {iOS,Android}/wpt__css-multicol__abspos-containing-block-outside-spanner.png
// both paint the test's two RED 100x100 probes UNCOVERED, where the frozen
// reference
// `tools/wpt/refs/<sha>/white-black-ink-font-lh-imgpad-htmlpins/css-multicol/
//  abspos-containing-block-outside-spanner.png`
// paints two GREEN squares at the initial containing block's top-left and
// bottom-right corners and no red at all. iOS puts ONE green square at the
// `position: relative` wrapper's origin (mid-document, under the paragraph);
// Android puts a 100x50 green sliver there — the spanner's own 50px height
// clamping it. Census row (redsquare-evidence.json): refGreen 8.55 /
// refRed 0 vs iOS 4.27 green / 8.55 red and Android 2.14 / 8.55.
//
// ## The document, and what CSS says about it
// ```html
// <div style="columns:3; column-gap:1em; width:20em;">      <!-- multicol -->
//   <div style="position:relative;">                        <!-- column item -->
//     <div style="column-span:all; height:50px;">           <!-- SPANNER -->
//       <div style="position:absolute; top:0; left:0;   …green…"></div>
//       <div style="position:absolute; bottom:0; right:0; …green…"></div>
// ```
// css-multicol-1 §6 ("Spanning Columns"): an element with
// `column-span: all` is taken OUT of its ancestors' block flow inside the
// multi-column container and laid out as a full-width block of the MULTICOL
// CONTAINER; the ancestors it was nested in fragment AROUND it. Those
// ancestors therefore do not geometrically contain the spanner, and CSS 2.1
// §10.1's "nearest positioned ancestor" search for an absolutely positioned
// descendant of the spanner must continue from the multi-column container
// outward — it may not stop at a `position: relative` box that lives in the
// column flow. Here nothing outside the multicol is positioned, so the two
// green boxes fall back to the INITIAL containing block and land on the
// canvas corners, exactly covering the red probes. That is precisely what
// the committed reference shows, and what the web runtime (which hands
// `column-span` straight to the browser) already produces: web scores
// 8.55 green / 0 red on this cell.
//
// ## Why this file exists rather than a patch inside the hoist
// [com.styleconverter.runtime.layout.position.CanvasRootHoist] owns the
// "does this box hoist to the ICB" truth table; the multicol rule above is
// a MULTICOL fact, so it lives in the multicol folder and the hoist calls
// into it. Both of the hoist's pure walks and ComponentRenderer's
// composition-side channel read these same two functions, so the walk and
// the composition can never disagree about where the chain breaks (that
// disagreement is the documented failure mode: a box dropped from flow with
// no overlay slot).
//
// ## Blast radius, enumerated before the change
// Scanning all 1435 per-test-IR documents of the 30 frozen wave-48 sections
// for a `ColumnSpan` component with an ABSOLUTE/FIXED descendant yields
// EXACTLY ONE test: css-multicol/abspos-containing-block-outside-spanner.
// 22 other tests carry spanners with no out-of-flow descendant, and for
// those every function here returns the pre-wave-49 value by construction —
// the flag it changes is read only by the ABSOLUTE and FIXED branches of
// `CanvasRootHoist.shouldHoistToCanvasRoot` /
// `rendersInFlowAsStaticPosition`. The scan is reproducible: walk each
// document's `slot.parent` tree, mark nodes carrying `ColumnSpan`, and test
// their descendants for `Position` in {ABSOLUTE, FIXED}.

// The IR property type the walks hand us (type + raw JSON payload).
import com.styleconverter.runtime.core.ir.IRProperty
// The positioned-ancestor predicate this rule composes with. Reused rather
// than re-derived so "is this box a containing block" has ONE definition
// across the hoist, the composition channel and this file.
import com.styleconverter.runtime.layout.position.CanvasRootHoist

object MulticolSpannerContainingBlock {

    /**
     * Is this declaration list a `column-span: all` box?
     *
     * Decoded through [MultiColumnExtractor] — the same reader
     * [MulticolSpannerFlow] uses to classify spanner rows — so this gate can
     * never disagree with the layout pass about what a spanner is.
     */
    fun isSpanner(properties: List<IRProperty>): Boolean =
        MultiColumnExtractor
            .extractMultiColumnConfig(properties.map { it.type to it.data })
            .span == ColumnSpan.ALL

    /**
     * Is this declaration list a multi-column CONTAINER?
     *
     * css-multicol-1 §3: a box becomes a multi-column container when
     * `column-count` or `column-width` is anything but `auto`. Both ride the
     * same [MultiColumnExtractor] decode as [isSpanner], so container
     * detection and spanner detection share one wire reader.
     */
    fun isMulticolContainer(properties: List<IRProperty>): Boolean =
        MultiColumnExtractor
            .extractMultiColumnConfig(properties.map { it.type to it.data })
            .let { it.columnCount != null || it.columnWidth != null }

    /**
     * The positioned-ancestor flag this node publishes for its CHILDREN.
     *
     * Ordinary nodes keep CSS 2.1 §10.1's OR-accumulation: a child has a
     * positioned ancestor if one already existed, or if this node is itself
     * positioned.
     *
     * A SPANNER restarts the accumulation from [positionedAtMulticol] — the
     * value that was in force at its multi-column container — because
     * css-multicol-1 §6.1 lays the spanner out as a block of that container,
     * so every box between the container and the spanner is skipped. The
     * spanner's OWN `position` still counts: it is a real box that really
     * does contain its descendants.
     *
     * [positionedAtMulticol] is null when there is no multi-column ancestor
     * at all. `column-span` has no effect outside a multi-column container
     * (css-multicol-1 §6.1: `all` spans "the nearest multicol ancestor in the
     * same block formatting context" — with no such ancestor there is nothing
     * to span), so a null keeps the ordinary OR-accumulation and this rule
     * stays inert.
     */
    fun childPositionedAncestor(
        ancestorPositioned: Boolean,
        positionedAtMulticol: Boolean?,
        properties: List<IRProperty>,
    ): Boolean {
        // The node's own contribution — identical in both branches, because
        // a spanner is skipped OVER, never skipped AS WELL.
        val self = CanvasRootHoist.establishesContainingBlock(properties)
        // The spanner branch: restart from the multicol container's context.
        if (positionedAtMulticol != null && isSpanner(properties)) {
            return positionedAtMulticol || self
        }
        // Everything else: the frozen wave-17 rule, byte-for-byte.
        return ancestorPositioned || self
    }

    /**
     * The "positioned-ancestor state at the multi-column container" this
     * node publishes for its CHILDREN.
     *
     * At a multi-column container it becomes [childPositionedAncestor] —
     * i.e. the chain INCLUDING the container itself, which really is an
     * ancestor of any spanner inside it. Everywhere else it is passed
     * through unchanged, so a spanner nested several levels down still sees
     * its own container's value. Nested multicols naturally resolve to the
     * innermost one, which is the container a `column-span: all` spans
     * (css-multicol-1 §6.1).
     */
    fun childPositionedAtMulticol(
        positionedAtMulticol: Boolean?,
        childPositionedAncestor: Boolean,
        properties: List<IRProperty>,
    ): Boolean? =
        if (isMulticolContainer(properties)) childPositionedAncestor
        else positionedAtMulticol

    /**
     * KNOWN LIMIT, stated rather than hidden (no silent fallthrough).
     *
     * The TRANSFORM half of the containing-block channel
     * ([CanvasRootHoist.LocalHasTransformedAncestor]) is deliberately NOT
     * restarted at a spanner. The same §6.2 argument would apply to a
     * transformed ancestor inside the column flow, but no test in the 30
     * frozen wave-48 sections carries that shape (the blast-radius scan in
     * this file's header found exactly one spanner-with-out-of-flow-
     * descendant test, and nothing on its chain is transformed), so
     * restarting it would be an unmeasured behaviour change. When such a
     * case arrives the fix is to thread a second `transformedAtMulticol`
     * value through the identical two functions above.
     *
     * This constant exists so the limit is greppable from the code, not only
     * from a comment.
     */
    const val TRANSFORM_CHAIN_RESTART_UNIMPLEMENTED: Boolean = true
}
