/**
 * RootPseudoPlacement — where a ROOT-SCOPE generated box (the body-root
 * component's `pseudos.before` / `pseudos.after`) is placed in the inline
 * axis, and when the body's own `direction` is allowed to decide that.
 *
 * ── WHY THIS MODULE EXISTS (wave-28, lane PG) ──────────────────────────
 * The WPT extractor merges every `html` / `body` / `:root` / `*` rule into
 * ONE synthetic component tagged `meta.role: 'body-root'`, and since
 * wave-27 A-RC2 it routes root-scope PSEUDO-ELEMENT rules (`html::before
 * { … }`) into that component's `pseudos` bucket instead of vandalising its
 * flat declaration bag. The generated box therefore renders as a child of
 * the BODY box even though the CSS attached it to the HTML box.
 *
 * That conflation is invisible until the body declares `direction: rtl`.
 * A block-level generated box narrower than its containing block is
 * over-constrained (CSS 2.1 §10.3.3): under an LTR containing block
 * `margin-right` is ignored and the box sits flush LEFT; under an RTL one
 * `margin-left` is ignored and it sits flush RIGHT. Nesting the html-owned
 * box inside the rtl body therefore pushed it right — MEASURED on
 * css-contain/contain-body-dir-001..004 in tools/titan/runs/wave27-final:
 * the ref paints the orange 100×100 square at image [16,16]-[115,115],
 * our web capture painted it at [116,16]-[215,115] — exactly 100 px, one
 * box width, to the right (iOS-web 0.8913 / Android-web 0.8960).
 *
 * ── THE SPEC RULE ──────────────────────────────────────────────────────
 * css-writing-modes-4 §3.2 propagates the BODY's `direction` to the
 * viewport (that is the only channel by which a body declaration can reach
 * a box generated on the root), and css-contain-1 §3.1 removes a contained
 * body from that channel — "if this is the body element, the used values
 * of writing-mode/direction are NOT propagated to the viewport". So on a
 * CONTAINED body-root the generated box keeps the root's own inline
 * direction, which is the initial `ltr`: physically LEFT. That is exactly
 * what the shared reference (contain-body-w-m-001-ref.html, "Test passes
 * if the orange square is in the upper-left corner") asserts for all 8
 * body-scope tests of the family (contain-body-dir-001..004 +
 * contain-body-w-m-001..004) and their 8 html-scope twins.
 *
 * ── THE MERGED html+body CAVEAT ────────────────────────────────────────
 * Because html and body share one bag, this module cannot tell
 * `html { contain }` from `body { contain }`, nor `html::before` from
 * `body::before`. Both conflations are harmless for the corpus and the
 * corpus proves it: contain-html-dir-001..004 put the containment on html
 * and the direction on body and match the SAME reference as
 * contain-body-dir-001..004. A `body::before` under an rtl NON-contained
 * body keeps today's behaviour (the gate below is false), so no existing
 * capture moves. The honest fix for the remaining ambiguity is a wire that
 * keeps html and body apart — not a guess here.
 *
 * Twinned 1:1 by Compose's `RootPseudoBox.kt` and SwiftUI's
 * `RootPseudoBox.swift`; the three renderers must not drift on this rule.
 */

import type { CSSProperties } from 'react';
import type { IRComponent } from '../core/ir/IRModels';

/**
 * Does this body-root's `Contain` leaf take the body OFF the
 * writing-mode/direction propagation path (css-contain-1 §3.1)?
 *
 * The token test is deliberately identical to the BACKGROUND-propagation
 * gate the three canvases already share (web harness
 * `bodyRootHasContainment`, Compose `containmentBlocksCanvasPropagation`,
 * SwiftUI `WPTCanvas.containmentBlocksPropagation`): TRUE iff at least one
 * token is a real containment keyword. The spec does not grade by
 * containment KIND for propagation questions and the corpus proves it —
 * contain-body-dir-001..004 set layout / paint / size / style
 * respectively (VERIFIED against the corpus sources, wave-28 skeptic — an
 * earlier draft of this comment said "content / strict" for 003/004, which
 * the test files do not declare) and all four match the same reference.
 * All four are also confirmed at the pin: the frozen chrome-150 refs for
 * dir-001..004 are byte-identical, 10 000 orange px at [16,16]-[115,115].
 * An absent or empty
 * list is not containment, and neither is `contain: none` (`["NONE"]`).
 *
 * Wire shape: the converter's `ContainProperty.values` list of uppercase
 * keywords. A bare string is tolerated too — the same defensive shape the
 * native twins already accept for this leaf.
 */
export function containmentBlocksDirectionPropagation(
  properties: IRComponent['properties'],
): boolean {
  // `Contain` is the only leaf that can answer this; absent ⇒ not contained.
  const prop = (properties as Array<{ type: string; data?: unknown }> | undefined)
    ?.find((p) => p.type === 'Contain');
  if (!prop) return false;
  // Array wire (normal emission) or a bare keyword string (defensive).
  const d = prop.data;
  const tokens = Array.isArray(d) ? d : (typeof d === 'string' ? [d] : []);
  // Any non-NONE token is containment; `[]` and `["NONE"]` are not.
  return tokens.some((t) => typeof t === 'string' && t.trim().toUpperCase() !== 'NONE');
}

/**
 * The placement style a root-scope generated box must carry, or `null`
 * when the box keeps the containing block's own inline direction.
 *
 * `marginRight: 'auto'` is the PHYSICAL pin, chosen on purpose over
 * `direction: ltr` on the box itself: a box's own `direction` does not
 * decide where its containing block puts it (CSS 2.1 §10.3.3 reads the
 * CONTAINING BLOCK's direction), so declaring `direction` on the span
 * would have changed nothing at all. An `auto` physical right margin
 * removes the over-constraint instead — the free space is absorbed on the
 * right in BOTH directions, so the box lands flush left exactly as the
 * uncontained root's own `ltr` would place it.
 *
 * Emitted BEFORE the author's own declarations at the call site, so a
 * `html::before { margin-right: 20px }` still wins.
 *
 * KNOWN GAP in that override (measured, wave-28 skeptic) — "the author
 * wins" holds only for declarations that write the SAME longhand. An
 * author `html::before { margin-left: auto }` (the way one asks for a
 * flush-RIGHT box under an ltr root) does not remove this pin, so both
 * margins end up `auto` and the box CENTRES. Chromium probe, 100px block
 * in a 200px ltr block: `margin-left:auto` alone → dx=100 (flush right),
 * `margin-right:auto` + `margin-left:auto` → dx=50 (centred). Left as-is
 * deliberately: no WPT test in the corpus declares a margin on a
 * root-scope pseudo at all (the 16 `contain:` + root-pseudo tests all use
 * the bare `content/width/height/background/display` set), so suppressing
 * the pin on a detected author `margin-left: auto` would be branching
 * with zero evidence behind it. Revisit if a fixture ever needs it.
 *
 * NOT YET COVERED — the block axis. `contain-body-w-m-001..004` declare
 * `writing-mode: vertical-rl` on the body instead of `direction: rtl`;
 * neutralising THAT needs the containing block's block-flow direction to
 * change, which no style on the generated box can express (the same
 * §10.3.3 reason above, one axis over).
 *
 * MEASURED (wave-28 skeptic, full 280-test css-contain web run): all 8
 * w-m tests (contain-{body,html}-w-m-001..004) still paint their orange
 * square at image [116,16]-[215,115] while the chrome-150 ref has it at
 * [16,16]-[115,115] — web-ref SSIM 0.9599, unchanged by this pin. In
 * `writing-mode: vertical-rl` the block axis runs right-to-left, so the
 * first block child stacks from the RIGHT edge; an auto right margin is
 * an INLINE-axis lever and cannot reach that. Confirmed in real Chromium
 * (headless probe, 100px block in a 200px `writing-mode: vertical-rl`
 * block): no pin → dx=100, `margin-right:auto` → dx=100, and even
 * `writing-mode: horizontal-tb` ON THE BOX ITSELF → dx=100.
 *
 * TODO(lane PG follow-up) — two candidate repairs, both out of this
 * lane's file set. (a) A full-inline-size shim WRAPPER around the
 * root-scope span (`display:block; width:100%; writing-mode:horizontal-tb;
 * direction:ltr`), which the same probe measured at dx=0/dy=0 and which
 * would fix both axes at once — but it adds a DOM level the natives'
 * pins do not have, and the probe also showed it is still incomplete when
 * writing-mode AND direction are both declared (dx=0 but dy=100), which no
 * corpus test does today. (b) The honest fix: an extractor-side root/body
 * split, so the html-owned box is not a child of the body box at all.
 * Blast radius of the whole question is bounded — a corpus-wide scan of
 * tools/wpt/css found exactly 16 tests combining `contain:` with a root
 * pseudo, the 8 dir tests this pin fixes and these 8.
 */
export function rootPseudoPlacementStyle(component: IRComponent): CSSProperties | null {
  // Only the synthetic root component can host a ROOT-SCOPE pseudo bucket;
  // every other component's ::before belongs to its own box and must keep
  // that box's direction.
  if (component.meta?.role !== 'body-root') return null;
  // Uncontained body ⇒ the body's direction really does propagate to the
  // root, so today's placement stands (and no capture moves).
  if (!containmentBlocksDirectionPropagation(component.properties)) return null;
  // Contained ⇒ pin to the physical inline-start of the root box.
  return { marginRight: 'auto' };
}
