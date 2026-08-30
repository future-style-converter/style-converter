package com.styleconverter.runtime.effects.clip

// ClipPathSubtreeScope — the one question the canvas-root hoist has to ask
// this category: does this box's `clip-path` bind its WHOLE subtree, the
// out-of-flow descendants included?
//
// ── WHY THIS EXISTS (measured, wave 49 lane A4) ────────────────────────
// css-masking-1 §5 ("Clipping Paths"): a non-`none` `clip-path` establishes
// a clipping region for the element "and its descendants" — the property has
// NO containing-block escape clause. That is what separates it from an
// `overflow` clip, which CSS 2.1 §11.1.1 explicitly lets a positioned
// descendant escape when its containing block lies outside the clipping box
// ("this property … does not apply to … boxes whose containing block is an
// ancestor of the clipping box"). So a `position: absolute|fixed` descendant
// escapes `overflow: hidden` but NEVER escapes an ancestor's `clip-path`.
//
// Compose can only express subtree clipping through the COMPOSITION tree:
// `Modifier.clip` clips the node's own draw pass and everything its children
// draw inside it. CanvasRootHoist lifts every ICB-anchored out-of-flow box
// out of that tree and re-renders it in the canvas-root overlay, which is a
// SIBLING of the clipping ancestor — so the clip is left behind.
//
// MEASURED consequence on the wave-48 gate, WPT
// `css-masking/clip-path/clip-path-blending-offset`
// (`#clip-path { clip-path: polygon(…) }` with an absolutely positioned,
// `mix-blend-mode: multiply` red 100×100 child at `left:40; top:50`):
//   Chromium ref  green 5100 px at [16,16]-[115,115], red NONE
//   web           identical to the ref (the DOM applies the clip natively)
//   iOS           identical to the ref
//   Android       green identical, PLUS a full red square at [56,66]-[155,165]
// i.e. 10 000 px of red that the polygon does not contain. android-ref SSIM
// 0.9566 with the colour veto tripped, the two other platforms 1.0000.
// (This is wave-49 backlog obligation #3 — the defect PR #126's
// BlendModeApplier saveLayer-bounds repair un-hid. Nothing here touches that
// repair.)
//
// ── WHAT THIS FILE DECIDES, AND WHAT IT COSTS ──────────────────────────
// It answers ONE predicate — "is this box's clip unescapable?" — which
// CanvasRootHoist consumes as a third ancestry channel next to its
// positioned-ancestor and transformed-ancestor flags. A box under such an
// ancestor is NOT hoisted; it renders inside that ancestor's subtree (so the
// `Modifier.clip` reaches it) with the zero-flow anchor the wave-18 RC1
// static-position branch already owns, so it still reserves no flow space
// (css-position-3 §2.1).
//
// The COST is named, not hidden: the box then anchors its insets against its
// flow slot inside the clipping ancestor instead of against the initial
// containing block (css-position-3 §3.1). The two coincide exactly when the
// clipping ancestor's content-box origin is the ICB origin — which is the
// shape of both corpus carriers — and differ by that ancestor's offset
// otherwise. Losing the anchor is strictly better than losing the clip: a
// mis-anchored box paints somewhere inside the clip, an unclipped one paints
// ink the reference does not contain at all (the 10 000 red px above).
//
// Scope is Android-only by construction. Web hands the subtree to the DOM,
// which applies the clip natively; iOS keeps the child in its SwiftUI
// hierarchy. Both already score 1.0000 on the carrier above, so neither has
// a twin of this file — the divergence is Compose's hoist, not the category.

import com.styleconverter.runtime.core.ir.IRProperty

/**
 * Does this declaration list establish a clip that its out-of-flow
 * descendants cannot escape?
 *
 * TRUE exactly when `clip-path` resolves to a real shape (css-masking-1 §5).
 * Read through [ClipPathExtractor] — the SAME decoder the live style chain
 * runs — so this predicate can never disagree with [ClipPathApplier] about
 * whether a clip is actually painted: an unparseable or `none` value yields
 * `ClipPathConfig.shape == null`, no `Modifier.clip` is emitted, and there is
 * therefore nothing for a descendant to escape.
 *
 * Deliberately NOT true for `overflow: hidden|clip|scroll|auto`: CSS 2.1
 * §11.1.1 lets a positioned descendant whose containing block is outside the
 * clipping box escape an overflow clip, and the canvas-root hoist reproduces
 * exactly that escape. Widening this predicate to overflow would break the
 * escape the spec requires.
 *
 * Deliberately NOT true for the legacy `clip` rect property either: it is
 * defined only for absolutely positioned elements and clips the element's
 * OWN box, not a descendant subtree the way `clip-path` does.
 *
 * @param properties the component's RAW base declarations — the same basis
 *   CanvasRootHoist's positioned/transformed ancestry flags use, so a
 *   selector/media bucket that introduces a `clip-path` at runtime is out of
 *   scope on all three channels alike (the collapse-plan bail-B6
 *   conservatism this repo applies to every hoist input).
 */
fun establishesUnescapableClip(properties: List<IRProperty>): Boolean =
    ClipPathExtractor
        .extractClipPathConfig(properties.map { it.type to it.data })
        .hasClipPath
