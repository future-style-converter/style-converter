package com.styleconverter.runtime.layout.flexbox

// FlexCrossStretch — css-flexbox-1 §8.3 CROSS-AXIS STRETCH for the LEGACY
// (non-wrapping) flex loops in ComponentRenderer.
//
// ## The measured defect (wave46-final Android captures, lane Z3)
// css-values/calc-size/calc-size-flex-001..006 + flex-009 paint ZERO green
// pixels on Android against a 100×100 green-square ref. The wave-42
// CalcSizeMinLayout floor DOES resolve the MAIN axis; what collapses is the
// CROSS axis: every item has an AUTO cross size and NO `align-self`, and the
// legacy loops only honoured stretch when `align-self: stretch` was EXPLICIT
// — `align-self: auto` resolving to the container's `align-items` (css-align-3
// §6.2 `auto`: "behaves as the computed align-items value of the parent
// box"), whose initial `normal` behaves as `stretch` for flex items
// (css-align-3 §6.2.4 / css-flexbox-1 §8.3), was never implemented there. A
// 100×0 (row) or
// 0×100 (column) item is zero ink. css-flexbox/align-self-011/012 (four
// `align-self: auto` items under `align-items: stretch`, 0.9966 with the
// same all-red capture) confirm the mechanism outside the calc-size family.
//
// ## Retro R2 (A11#12): the composed-WPT gate is GONE — stretch is CSS, not
// a capture mode
// Wave 47 armed the `auto` arm and the column explicit-stretch arm ONLY
// under composed-WPT capture, on the reasoning that the dark-stage web
// harness wraps unsized children in `width: fit-content` (a definite cross
// size, under which §8.3 degrades stretch to flex-start), so NOT stretching
// there was "web parity". The audit (A11#12, fidelity trees) measured what
// that parity costs: `align-self: stretch` / default-stretch width-less
// children in COLUMN flex containers render content-sized on Android and
// web (FC_AlignSelf `d` 48 wide, N3_ColumnOfRows inner rows ~120) while
// iOS stretches them (188 / 264 wide) — three IOS-ODD rows where the odd
// platform is the one following css-flexbox-1 §9.4 step 11 ("if a flex item
// has align-self: stretch, its computed cross size property is auto, and
// neither of its cross-axis margins are auto, the used outer cross size is
// the used cross size of its flex line"). The web harness's fit-content
// initialiser is the defect (queued as its own PR because it moves every
// committed web baseline); the Compose engine now follows the spec in
// every mode. Blast radius, measured not assumed: the composed WPT corpus is
// unchanged (the arms already fired there — the harness provides
// LocalWptComposedMode = true for every titan capture). Executed scan of the
// converter IR of every non-WPT fixture (435 docs, fixtures/visual-test.json
// — the 327-net — included): fixtures/visual-test.json has ZERO column
// containers with definite width + width-less stretching children and ZERO
// row containers with definite height + height-less `auto` items. The
// dark-stage movers are exactly fixtures/fidelity/trees {flex-column
// FC_AlignSelf d, nested-3level N3_ColumnOfRows row1/row2, mixed-direction
// MD_ColumnWithRow innerRow} (column axis; toward iOS, away from web's
// fit-content) and fixtures/properties/layout/flex-align-items AI_Stretch
// a/b/c (row axis, `auto` arm — the web harness never fit-contents HEIGHT,
// so this one moves toward web as well). The two other row hits of that
// scan (flex-row FR_AlignSelf d, self-alignment PL_FlexAlignOverride c) are
// EXPLICIT row stretch = the frozen legacy arm, which already fired.
//
// ## Gates (each is a spec rule or a measured-risk containment, not a hedge)
//  * CONTAINER CROSS SIZE DEFINITE: §9.4 step 8 grows the line to the
//    container's definite cross size; with an AUTO container cross size the
//    line's cross size is the largest item's, which a plain fillMax* cannot
//    express — Compose would fill the canvas remainder instead (the exact
//    blowout FlexboxApplier documents for weight), so the auto/column arms
//    stand down there and the item keeps its content cross size.
//  * CHILD CROSS SIZE AUTO: §8.3 — stretch only applies when the item's
//    cross size property computes to `auto`.
//  * IN-FLOW ONLY: §4.1 — an absolutely-positioned child is not a flex item.
//  * BOX-GENERATING ONLY: css-display-3 §2.5 — a `display: contents` element
//    generates NO box and is therefore not a flex item (its children are);
//    `display: none` generates nothing at all. Both keep the legacy measure.
//  * The row `align-self: stretch` arm keeps its historical all-modes,
//    no-container-gate behaviour byte-identically (the 327-pair baseline).
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
     * @param alignSelf the child's own align-self claim (AUTO when absent).
     * @param rowAxis true in RenderRowContent (cross = height), false in
     *   RenderColumnContent (cross = width).
     * @param containerItemsStretch [containerAlignItemsStretches] of the
     *   container — what `align-self: auto` resolves to (css-align-3 §6.2 — retro R2 corrected the §6.4 miscite).
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
     * §6.2 also EXEMPT an item whose cross-axis margins include `auto`
     * from stretching — this gate has no auto-margin input, so such an
     * item stretches where the spec says it must not. Zero corpus cells
     * carry a cross-axis auto margin in a firing position today (executed
     * scan, wave 47), and the frozen explicit-stretch arms share the same
     * blindness — add a childHasCrossAutoMargin input (default false)
     * before any margin-bearing flex family lands.
     */
    fun effectiveStretch(
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
        // first preserves the 327-pair baseline exactly.
        if (rowAxis && alignSelf == ComponentRenderer.AlignSelf.STRETCH) return true
        // ── Spec arms — every capture mode (retro R2 removed the gate) ────
        // Boxless children (display: contents/none) are not flex items.
        if (!childGeneratesBox) return false
        // No definite line-growth target → a fillMax* would fill the canvas
        // remainder, not the line (see the banner's blowout rationale).
        if (!containerCrossDefinite) return false
        return when (alignSelf) {
            // `auto` resolves to the container's align-items (css-align-3
            // §6.2); initial `normal` behaves as stretch for flex items
            // (css-align-3 §6.2.4 / css-flexbox-1 §8.3) — the
            // wave-47 arm that repaints calc-size-flex-001..009's items,
            // now also the dark-stage AI_Stretch / N3_ColumnOfRows rows.
            ComponentRenderer.AlignSelf.AUTO -> containerItemsStretch
            // Explicit stretch on the COLUMN axis: §9.4 step 11 — the item
            // fills the line's cross size (its width). Wave 47 fired this
            // only under composed capture; the dark-stage FC_AlignSelf `d`
            // (the audit's 48-vs-188 row) is exactly this arm.
            ComponentRenderer.AlignSelf.STRETCH -> !rowAxis
            // start/end/center/baseline are positional, never a stretch.
            else -> false
        }
    }
}
