package com.styleconverter.runtime.core.renderer

// Wave 52 (lane L3 · failure-ink, fix F1) — the POST-LOAD-EXTRACTION
// SIGNATURE, one predicate shared by every native seam that must decline
// to re-lay-out a box whose wire already carries the browser's USED layout.
//
// ## The measured defect
// Two Compose seams decline such boxes: the wave-47 (lane Z2) vertical
// block-flow seam (ComponentRenderer's `anyBakedChild`, consumed where the
// seam decides between VerticalBlockFlowLayout and the frozen Column) and
// the vertical multicol fragmentation pass (MulticolSpannerFlow.ChildSpec
// .bakedPhysicalSize, read by VerticalMulticolMeasure). Both used the
// wave-47 heuristic "the child declares BOTH Width and Height" — which is
// NOT the extractor's signature. An ordinary author rule such as
// `.square { width: 50px; height: 50px }` trips it, so on
// css-writing-modes/flexbox_align-items-stretch-writing-modes the seam kept
// the frozen Column for the two vertical-writing-mode flex items: their
// squares stacked VERTICALLY, each item came out 50px wide instead of 100,
// and the test's own RED failure ink (a 250×100 `z-index:-1` probe) showed
// through the right 100px — wave51-fix ios f 0.999 · android P 0.998, the
// same picture on both natives (SwiftUI twin: `verticalBlockFlowZ2`).
//
// ## The real signature (what the extractor writes on EVERY box it bakes)
// `tools/titan/post-load-extract.mjs` (POST_LOAD_PROPERTIES, lines 160-181)
// writes `width` + `height` + `box-sizing` ("rides along so the basis is
// always explicit"), the four `margin-*`, the four `padding-*`, and the
// four `border-*-{width,style,color}` — in IR type names: Width, Height,
// BoxSizing, Padding{Top,Right,Bottom,Left}, Border{Top,…}{Width,Style,Color}.
// Census over the 1435 wave51-fix per-test IR documents
// (tools/titan/results/wave52-failure-ink/census.json, F1): every
// Width+Height child of the 9 post-load-extracted carrier documents (12
// containers: the anchor-position-multicol family ×7, -colspan-003,
// input-range-zero-inline-size ×4) carries all of BoxSizing + PaddingTop +
// BorderTopStyle; every Width+Height child of the 5 authored carrier
// documents (8 containers) carries NONE of the three. The used-writing-mode
// pass (census.json f1Used) adds authored table cells that DO carry padding
// and a border style — but never BoxSizing, which is why the basis is the
// load-bearing conjunct. The predicate below is the smallest conjunction
// that separates the populations with a margin: BoxSizing AND at least one
// decoration-band longhand, so a future authored `box-sizing: border-box`
// alone can never re-trip a guard.
//
// ## Parity
// SwiftUI twin: Renderer/BakedLayoutSignature.swift — byte-parallel rule,
// pinned on both natives by VerticalBlockFlowSeamGuardTest(s) over the
// VERBATIM wave51-fix payloads (authored squares → false, baked box → true).

// The wire property (IR type name + raw JSON payload) the seams hand us.
import com.styleconverter.runtime.core.ir.IRProperty

object BakedLayoutSignature {

    /** The used physical inline size the extractor bakes (`width`). */
    private const val WIDTH = "Width"

    /** The used physical block size the extractor bakes (`height`). */
    private const val HEIGHT = "Height"

    /** The sizing basis that "rides along" with every baked size pair. */
    private const val BOX_SIZING = "BoxSizing"

    /** One of the four baked padding bands (any side would do; top is fixed). */
    private const val PADDING_TOP = "PaddingTop"

    /** One of the twelve baked border longhands (style is never absent). */
    private const val BORDER_TOP_STYLE = "BorderTopStyle"

    /**
     * True iff [properties] carries the post-load extractor's signature —
     * a box whose used physical layout is ALREADY on the wire, so a native
     * layout seam must keep its frozen render rather than lay it out again.
     *
     * Presence is the signal, never the value: the extractor writes the
     * longhands whatever they resolve to (`padding-top: 0px`,
     * `border-top-style: none` are on every baked box above), while an
     * authored document only carries what the author declared.
     */
    fun bakedPhysicalBox(properties: List<IRProperty>): Boolean {
        // One pass over the declaration list — presence set of IR types.
        val types = properties.mapTo(HashSet(properties.size)) { it.type }
        // Width AND Height (the wave-47 half of the rule) …
        return WIDTH in types && HEIGHT in types &&
            // … AND the sizing basis the extractor always writes with them …
            BOX_SIZING in types &&
            // … AND at least one decoration band (padding or border style).
            (PADDING_TOP in types || BORDER_TOP_STYLE in types)
    }
}
