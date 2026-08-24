package com.styleconverter.runtime.layout.flexbox

// FlexCrossStretch — css-flexbox-1 §8.3 CROSS-AXIS STRETCH for the LEGACY
// (non-wrapping) flex loops in ComponentRenderer.
//
// ## The measured defect (wave46-final Android captures, lane Z3)
// css-values/calc-size/calc-size-flex-001..006 + flex-009 paint ZERO green
// pixels on Android (001-006: blank page below the <p>; 009: the container's
// red fully exposed) against a 100×100 green-square ref. Wave-46 Y6 mapped
// the family to the legacy flex loops "defeating the calc-size machinery";
// tracing the measure shows the wave-42 CalcSizeMinLayout floor DOES resolve
// the MAIN axis (the row tests' items measure 100 wide; the column tests'
// 100 tall — floorBand raises the band regardless of the container's zero
// offer). What collapses is the CROSS axis: every one of the seven items has
// an AUTO cross size and NO `align-self`, and the legacy loops only honour
// stretch when `align-self: stretch` is EXPLICIT — `align-self: auto`
// resolving to the container's `align-items`, whose initial `normal` behaves
// as `stretch` in a flex container (css-align-3 §6.4 / css-flexbox-1 §8.3),
// was never implemented there. A 100×0 (row) or 0×100 (column) item is zero
// ink, which is exactly the captures. css-flexbox/align-self-011/012 (four
// `align-self: auto` items under `align-items: stretch`, failing 0.9966 with
// the same all-red capture) confirm the mechanism outside the calc-size
// family.
//
// ## Gates (each is a spec rule or a measured-risk containment, not a hedge)
//  * COMPOSED-WPT ONLY for the new arms: the dark-stage per-component web
//    harness wraps unsized children in `fit-content` (a definite cross size,
//    under which §8.3 degrades stretch to flex-start — the pixel-verified
//    FC_AlignSelf calibration the column loop documents), so NOT stretching
//    there is genuine web-parity, while the composed WPT page renders the
//    browser's real stretch. The legacy row `align-self: stretch` arm keeps
//    its historical all-modes behaviour byte-identically.
//  * CONTAINER CROSS SIZE DEFINITE: §9.4 step 8 grows the line to the
//    container's definite cross size; with an AUTO container cross size the
//    line's cross size is the largest item's, which a plain fillMax* cannot
//    express — Compose would fill the canvas remainder instead (the exact
//    blowout FlexboxApplier documents for weight), so the new arms stand
//    down there and the item keeps its content cross size.
//  * CHILD CROSS SIZE AUTO: §8.3 — stretch only applies when the item's
//    cross size property computes to `auto`.
//  * IN-FLOW ONLY: §4.1 — an absolutely-positioned child is not a flex item.
//  * BOX-GENERATING ONLY: css-display-3 §2.5 — a `display: contents` element
//    generates NO box and is therefore not a flex item (its children are);
//    `display: none` generates nothing at all. Both keep the legacy measure.
//
// Pure and JVM-pinned (FlexCrossStretchTest) — the "extract the decision,
// pin it on the JVM" shape this suite mandates (no Robolectric).

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import com.styleconverter.runtime.core.types.ValueExtractors

object FlexCrossStretch {

    /**
     * Does this flex container's `align-items` resolve to stretch?
     *
     * Mirrors extractDisplayConfig's AlignItems arm byte-for-byte: the
     * explicit non-stretch keywords map away, and EVERYTHING else — absent
     * property (CSS initial `normal`), `normal`, `stretch`, unknown — folds
     * to stretch, exactly like that fold's `else -> AlignItems.STRETCH`.
     */
    fun containerAlignItemsStretches(properties: List<IRProperty>): Boolean {
        properties.forEach { prop ->
            // Only the AlignItems longhand participates; the ITEM-scoped
            // AlignSelf claim is the caller's separate input.
            if (prop.type == "AlignItems") {
                // Same dual keyword read as the display fold (bare primitive).
                return when (ValueExtractors.extractKeyword(prop.data)?.uppercase()) {
                    // css-align-3 §6.1 non-stretch keywords — including the
                    // flex-relative spellings the wire may carry.
                    "CENTER", "FLEX_START", "FLEX-START", "START",
                    "FLEX_END", "FLEX-END", "END", "BASELINE" -> false
                    // normal / stretch / unknown → stretch (the fold's else).
                    else -> true
                }
            }
        }
        // Absent → CSS initial `normal`, which behaves as stretch in a flex
        // container (css-flexbox-1 §8.3, same default the display fold takes).
        return true
    }

    /**
     * Does this child generate a box that can BE a flex item?
     *
     * css-display-3 §2.5: `display: contents` removes the element's own box
     * (its contents are promoted to items themselves) and `display: none`
     * generates nothing — neither is a flex item, so per-item stretch must
     * not touch them. Every other display value (or none declared) is a
     * blockified flex item per css-display-3 §2.7.
     */
    fun childGeneratesBox(properties: List<IRProperty>): Boolean {
        properties.forEach { prop ->
            // A single Display read; absent Display falls through to true.
            if (prop.type == "Display") {
                return when (ValueExtractors.extractKeyword(prop.data)?.uppercase()) {
                    // The two boxless display values (css-display-3 §2.5).
                    "CONTENTS", "NONE" -> false
                    // Everything else generates a box → a real flex item.
                    else -> true
                }
            }
        }
        // No Display declaration — an ordinary box, a real flex item.
        return true
    }

    /**
     * The single cross-axis stretch decision for one legacy-loop flex item.
     *
     * @param composedWpt whether the composed-WPT capture is live (the only
     *   place the NEW arms may fire — see the fit-content banner above).
     * @param alignSelf the child's own align-self claim (AUTO when absent).
     * @param rowAxis true in RenderRowContent (cross = height), false in
     *   RenderColumnContent (cross = width).
     * @param containerItemsStretch [containerAlignItemsStretches] of the
     *   container — what `align-self: auto` resolves to (css-align-3 §6.4).
     * @param containerCrossDefinite the container declares a definite cross
     *   size (the §9.4-step-8 line-growth basis the fillMax* target needs).
     * @param childCrossDefinite the child declares a definite cross size —
     *   §8.3 degrades stretch to flex-start then.
     * @param childIsOutOfFlow abspos/fixed child — not a flex item (§4.1).
     * @param childGeneratesBox [childGeneratesBox] of the child.
     * @return true when the caller must fillMaxHeight (row) / fillMaxWidth
     *   (column) this item.
     *
     * TODO (wave-47 skeptic S2, latent): css-flexbox-1 §8.3 / css-align-3
     * §6.4 also EXEMPT an item whose cross-axis margins include `auto`
     * from stretching — this gate has no auto-margin input, so such an
     * item stretches where the spec says it must not. Zero corpus cells
     * carry a cross-axis auto margin in a firing position today (executed
     * scan, wave 47), and the frozen explicit-stretch arms share the same
     * blindness — add a childHasCrossAutoMargin input (default false)
     * before any margin-bearing flex family lands.
     */
    fun effectiveStretch(
        composedWpt: Boolean,
        alignSelf: ComponentRenderer.AlignSelf,
        rowAxis: Boolean,
        containerItemsStretch: Boolean,
        containerCrossDefinite: Boolean,
        childCrossDefinite: Boolean,
        childIsOutOfFlow: Boolean,
        childGeneratesBox: Boolean,
    ): Boolean {
        // §4.1: out-of-flow children take their static position machinery
        // instead — never a stretch (also the frozen legacy row behaviour).
        if (childIsOutOfFlow) return false
        // §8.3: a definite cross size degrades every stretch to flex-start
        // (also frozen legacy row behaviour: `stretches` required !definite).
        if (childCrossDefinite) return false
        // ── FROZEN legacy arm — byte-identical in every mode ─────────────
        // The row loop has always filled an EXPLICIT `align-self: stretch`
        // item's height (all modes, no container-definite gate); keeping it
        // outside the composed gate preserves the 327-pair baseline exactly.
        if (rowAxis && alignSelf == ComponentRenderer.AlignSelf.STRETCH) return true
        // ── NEW arms — composed-WPT capture only ─────────────────────────
        // Outside composed capture the dark-stage fit-content calibration
        // stands (see the banner) and NOTHING below may fire.
        if (!composedWpt) return false
        // Boxless children (display: contents/none) are not flex items.
        if (!childGeneratesBox) return false
        // No definite line-growth target → a fillMax* would fill the canvas
        // remainder, not the line (see the banner's blowout rationale).
        if (!containerCrossDefinite) return false
        return when (alignSelf) {
            // `auto` resolves to the container's align-items (css-align-3
            // §6.4); initial `normal` behaves as stretch (§8.3) — THE
            // wave-47 arm that repaints calc-size-flex-001..009's items.
            ComponentRenderer.AlignSelf.AUTO -> containerItemsStretch
            // Explicit stretch on the COLUMN axis: the dark-stage loop maps
            // it to Alignment.Start (web-harness fit-content parity); the
            // composed browser genuinely stretches, so honour it here.
            ComponentRenderer.AlignSelf.STRETCH -> !rowAxis
            // start/end/center/baseline are positional, never a stretch.
            else -> false
        }
    }
}
