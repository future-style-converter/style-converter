package com.styleconverter.runtime.core.placement

// ItemPlacement — the child-carried placement union of the IR v2 Slot &
// Placement contract (design §2.2, schema/spec/03-children.md §scope).
//
// ITEM-scoped CSS properties (align-self, justify-self, order, flex-*,
// grid-row/column lines, z-index) are declared on the CHILD and activate
// only when it lands in a matching container. This file is the single
// in-memory carrier for those claims on Android: one config, one
// extractor (ItemPlacementExtractor), one ParentDataModifier element
// (ItemPlacementElement) — replacing the scattered inline peeks the
// renderer used to do per call site (the pattern that let the
// ANCHOR_CENTER fix land in dead copies while the live path lagged).
//
// Resolution rule (design §2.2): a container consumes ONLY the block
// matching its own kind — GridRenderer reads [grid] (+ the shared
// alignment/order claims), the flex paths read [flex] (+ shared claims).
// Blocks for other container kinds are inert data, exactly like
// `grid-area` on a flex child in a browser.

import com.styleconverter.runtime.core.renderer.ComponentRenderer

/**
 * Explicit grid placement claims, in 1-based CSS grid lines (css-grid-1
 * §8.3). Null = auto.
 *
 * The numeric fields keep their original pinned semantics (only
 * `{type:number}` / bare-integer wires populate them — negative integers
 * included, css-grid-1 §8.3 "negative integers count from the end of the
 * explicit grid"). Wave 5 adds the two remaining wire flavors the
 * GridColumn/RowStart parsers emit, previously dropped as auto:
 *   - `{type:span, count:N}`  → the *Span fields ("span N" claims);
 *   - `{type:name, name:X}`   → the *Name fields. These carry named-line /
 *     named-area idents — critically, the converter's GridAreaExpander
 *     lowers `grid-area: media` to a SINGLE `grid-row-start: media`
 *     longhand, so area-name placement arrives through rowStartName.
 */
data class GridClaims(
    val colStart: Int? = null,
    val colEnd: Int? = null,
    val rowStart: Int? = null,
    val rowEnd: Int? = null,
    /** `span N` on grid-column-start / -end (css-grid-1 §8.3 span syntax). */
    val colStartSpan: Int? = null,
    val colEndSpan: Int? = null,
    /** `span N` on grid-row-start / -end. */
    val rowStartSpan: Int? = null,
    val rowEndSpan: Int? = null,
    /** Named-line / named-area idents (css-grid-1 §8.3 <custom-ident>). */
    val colStartName: String? = null,
    val colEndName: String? = null,
    val rowStartName: String? = null,
    val rowEndName: String? = null
)

/**
 * Flex sizing claims (css-flexbox-1 §7). Defaults are the CSS initial
 * values — grow 0, shrink 1, basis auto (null) — applied for absent
 * fields per the design's resolution rule.
 */
data class FlexClaims(
    val grow: Float = 0f,
    val shrink: Float = 1f,
    /** Used flex basis in px; null = auto/content (not statically resolvable). */
    val basisPx: Double? = null
)

/**
 * The per-component placement union. One instance per component,
 * produced by [ItemPlacementExtractor] and carried to the measuring
 * container as Compose parent-data via [itemPlacement].
 *
 * The alignment / order / paint claims sit at the top level rather than
 * inside a per-kind block because CSS defines each as ONE property
 * consumed by more than one formatting context (`align-self` by flex
 * items AND grid items, `order` by flex order AND grid auto-placement
 * order, `z-index` by all positioned/flex/grid paint order) — one
 * extraction, per-kind consumption.
 */
data class ItemPlacement(
    val grid: GridClaims = GridClaims(),
    val flex: FlexClaims = FlexClaims(),
    /** Cross-axis self-alignment (css-align-3 §6.4). AUTO = defer to container. */
    val alignSelf: ComponentRenderer.AlignSelf = ComponentRenderer.AlignSelf.AUTO,
    /** Inline-axis self-alignment (css-align-3 §6.2) — grid + block-level only; flex items ignore it. */
    val justifySelf: ComponentRenderer.JustifySelf = ComponentRenderer.JustifySelf.AUTO,
    /** Visual reordering (css-display-3 / css-flexbox-1 §5.4). CSS initial 0. */
    val order: Int = 0,
    /** Paint order among siblings (CSS 2.1 §9.9). Null = auto. */
    val zIndex: Int? = null
) {
    companion object {
        /** All-defaults instance — a component with no ITEM claims. */
        val Default = ItemPlacement()
    }
}
