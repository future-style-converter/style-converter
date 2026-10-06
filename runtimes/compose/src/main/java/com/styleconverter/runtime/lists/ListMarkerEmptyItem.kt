package com.styleconverter.runtime.lists

// The EMPTY list item — wave 52, lane L6 (T7's native half). BYTE-PARALLEL
// TWIN of runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/
// ListMarkerEmptyItem.swift.
//
// ## The defect (probed, not assumed)
// Lane L5's F-E (tools/titan/results/wave52-extractor-cascade/
// seam-6-FE-li.patch) stops the extractor turning a rule-less `<li></li>`
// into a 100×100 placeholder: the 51 `<li>` of the 15
// css-counter-styles/cssom/ tests (+ none elsewhere) reach the wire with
// NO text, NO children, NO pseudo and NO declared size. Every one sits
// under `list-style-position: inside`, so both natives route it to the
// inside OVERLAY (ListMarkerRow.rendersInsideOverlay: inside + no text
// baseline), which paints the marker in a ZERO-size box over an item that
// is itself 0 tall — so every row collapses onto the first. Probed on iOS
// (Catalyst raster of the post-F-E cssom-pad-setter-invalid wire with the
// T7 bake's "001." / "002." / "003."): ONE ink band y 16–28 for the three
// markers, where the ref paints three rows at a 20 px pitch (y 36–47,
// 56–67, 76–87). The T7 native flips cannot land on that picture.
//
// ## The spec
// css-lists-3 §3.5: an `inside` ::marker is the item's FIRST INLINE BOX —
// for an item with no other content it is the ONLY content of the item's
// one line box, and an auto-height block is as tall as its line boxes
// (CSS 2.1 §10.6.3). So for such an item the marker must SIZE the item:
// the leading-inline-box row (marker + empty item) is that layout; the
// zero-size overlay erases it. Symmetrically an `outside` marker on an
// empty item still sits on a line box of the item's own (Blink lays an
// empty `<li>` out one line tall), so the hang reports the marker's height
// for it too (ListMarkerOutsideHang.place `itemIsEmpty`).
//
// ## What "empty" means here (IR-provable, deliberately narrow)
// No own text, no composed children, no generated ::before / ::after /
// ::marker content (`pseudos` — counter-list-item-2/-3's `<li>` carry a
// baked ::before and are NOT empty), and no declared block size (an item
// with `height` keeps its own box; the overlay is right for it — the
// abspos-only counter-styles items declare `height: 31.25px`).
// Corpus (census.mjs, wave51-fix wire): 12 such `<li>` today, all in
// counter-style-at-rule/name-case-sensitivity (floated, failing ×3); 51 more
// after F-E (the cssom tests). The renderer seams consult this predicate
// (seam-3.patch / seam-4.patch); nothing reads it before they land.

import com.styleconverter.runtime.core.ir.IRComponent

object ListMarkerEmptyItem {

    /** The IR property types that give an item a block size of its own —
     *  css-sizing-3 §3 `height` / `min-height` and their logical twins. */
    private val DECLARED_BLOCK_SIZE = setOf("Height", "MinHeight", "BlockSize", "MinBlockSize")

    /**
     * Is [item] content-free, so that its marker is the only thing that can
     * give it a line box (see the header for each clause's reason)?
     */
    fun isEmpty(item: IRComponent): Boolean =
        // Own in-flow text would make the line box itself.
        item._text.isNullOrEmpty() &&
            // Any child (in-flow or not) is content this rule does not model.
            item.children.isNullOrEmpty() &&
            // Generated content (a baked ::before counter) is content too.
            item.pseudos.isNullOrEmpty() &&
            // A declared block size is the item's own box — keep the overlay.
            item.properties.none { it.type in DECLARED_BLOCK_SIZE }

    /**
     * The inside-overlay decision with emptiness folded in: the overlay
     * (ListMarkerRow.rendersInsideOverlay) only when the item is NOT empty;
     * an empty item takes the row, where the marker sizes the line.
     */
    fun rendersInsideOverlay(position: ListStylePosition?, item: IRComponent): Boolean =
        ListMarkerRow.rendersInsideOverlay(position, ListMarkerRow.itemExposesTextBaseline(item)) &&
            !isEmpty(item)
}
