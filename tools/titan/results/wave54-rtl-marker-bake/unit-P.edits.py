#!/usr/bin/env python3
# tools/titan/results/wave54-rtl-marker-bake/unit-P.edits.py — the five hunks of unit P on tools/titan/bidi-bake.mjs,
# authored as exact-string replacements on HEAD db6e8aa0's bytes (each must match once). The logic is wave-53 hunk P
# of a8ffd1c6 in its fix-pass state (git show b0edb788^:tools/titan/bidi-bake.mjs), byte-for-byte in code; only the
# comments are re-labelled for wave 54 and re-pointed at files that exist today.
import sys
P = 'tools/titan/bidi-bake.mjs'
s = open(P, encoding='utf-8').read()
pairs = [
# 1. rootProperties takes the walk record (defaulted, so every pre-wave-54 caller and pin is unchanged).
("""export function rootProperties(rect, position) {
  const props = {""",
"""export function rootProperties(rect, position, el = null) {
  const props = {"""),
# 2. …and emits `padding: 0` for a spent padding, right after the position rule.
("""  if (position === 'static') props.position = 'relative';
  return props;
}

/**
 * Property map for a baked RUN.""",
"""  if (position === 'static') props.position = 'relative';
  // Wave-54 lane L1 unit P — see paddingIsSpent. applyBidiBakePlan deletes
  // every padding longhand / logical side before it merges this one.
  if (paddingIsSpent(el)) props.padding = '0';
  return props;
}

/**
 * Wave-54 lane L1 unit P (wave-53 hunk P of a8ffd1c6, fix-pass state
 * b0edb788^, re-landed as its own revert unit): is this bake root's padding
 * a SPENT input?
 *
 * After the bake every surviving descendant of a root is ABSOLUTELY
 * positioned (or hidden — `plan.hides` gives the `<br>`s `display: none`,
 * so they generate no box), and an abspos box resolves its insets against
 * the PADDING box of its containing block (CSS 2.1 §10.1 item 4;
 * css-position-3 §3.1). The root also carries `box-sizing: border-box` with
 * its used width/height, so its padding box is the border box minus borders
 * whatever the padding is: zeroing it moves nothing on web or iOS. Android
 * anchors abspos children at the CONTENT box (runtimes/compose/…/layout/
 * position/PositionedParentFlowSlot.kt:74-76, a documented approximation),
 * so a padding left on the wire there shifts every baked run by it —
 * measured: counter-suffix's RTL `foo` at x152 where ref/web/iOS have x104
 * (`ol { padding: 0 3em }`), bidi-lines-001/-002 +4 px (`0 0.5ch`); with
 * these bytes at wave53-probe bidi-lines-001 android went f 0.8934 → P
 * 0.9629 while web/iOS stayed decoded-pixel identical
 * (tools/titan/results/wave54-plan/rtl-marker-bake.md §4).
 *
 * Only a root whose browser-resolved padding is NON-ZERO on some side is
 * rewritten: a zero-padding root keeps its `padding*` keys in place and in
 * order, so its per-test IR stays byte-identical (selectors/dir-style-02a,
 * dir-selector-change-003/-004 — tools/titan/results/wave53-plan/
 * rtl-marker-bake.padding-census.py). And never where the padding still
 * paints or clips something: `background-clip`/`-origin: content-box`
 * (css-backgrounds-3 §2.7-2.8) or a non-`visible` overflow (css-overflow-3
 * §3: the padding box is the scroll container's clip edge). `el` null (every
 * pre-wave-54 caller and pin) → false.
 */
export function paddingIsSpent(el) {
  // The walker's four resolved sides, top/right/bottom/left in px.
  if (!Array.isArray(el?.padding) || !el.padding.some((v) => v !== 0)) return false;
  // Any layer clipped/positioned to the content box keeps its padding.
  if (/content-box/.test(`${el.backgroundClip ?? ''} ${el.backgroundOrigin ?? ''}`)) return false;
  // A clipping/scrolling root keeps the padding its clip edge depends on.
  return !(el.overflow ?? []).some((v) => v && v !== 'visible');
}

/**
 * Property map for a baked RUN."""),
# 3. The in-page walker records the resolved padding and the two guards' inputs.
("""        display: cs.display,
        position: cs.position,
        // Decorations propagate""",
"""        display: cs.display,
        position: cs.position,
        // Wave-54 lane L1 unit P: the resolved padding (top/right/bottom/left
        // px) and the two guards paddingIsSpent reads before zeroing it.
        padding: [cs.paddingTop, cs.paddingRight, cs.paddingBottom, cs.paddingLeft].map((v) => parseFloat(v) || 0),
        backgroundClip: cs.backgroundClip,
        backgroundOrigin: cs.backgroundOrigin,
        overflow: [cs.overflowX, cs.overflowY],
        // Decorations propagate"""),
# 4. planBidiBake hands the root's walk record to rootProperties.
("""      plan.roots.push({ path: e.path, props: rootProperties(e.rect, e.position) });""",
"""      // Unit P: the walk record carries the resolved padding paddingIsSpent reads.
      plan.roots.push({ path: e.path, props: rootProperties(e.rect, e.position, e) });"""),
# 5. applyBidiBakePlan: a spent padding leaves as ONE `padding: 0`.
("""    if (!cmp) throw new Error(`bidi-bake: no component at ${path.join('.')}`);
    Object.assign(cmp.properties ??= {}, props);
    delete cmp._text;
    cmp._lossy = true;""",
"""    if (!cmp) throw new Error(`bidi-bake: no component at ${path.join('.')}`);
    // Unit P: a spent padding leaves as ONE `padding: 0` — every longhand and
    // logical side goes first, so none can outlive it; an authored shorthand
    // keeps its key position (Object.assign overwrites it in place — all six
    // non-zero roots of the corpus carry only the shorthand, padding census).
    if ('padding' in props) {
      for (const k of Object.keys(cmp.properties ?? {})) if (k.startsWith('padding-')) delete cmp.properties[k];
    }
    Object.assign(cmp.properties ??= {}, props);
    delete cmp._text;
    cmp._lossy = true;"""),
]
for i, (old, new) in enumerate(pairs):
    n = s.count(old)
    if n != 1: sys.exit(f'pair {i}: found {n} times — nothing written')
    s = s.replace(old, new)
open(P, 'w', encoding='utf-8').write(s)
print(f'{P}: {len(pairs)} unit-P hunks applied')
