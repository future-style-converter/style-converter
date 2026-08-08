// LineClampApplier.ts — emits CSS declarations from a LineClampConfig.
// Web is the privileged platform for typography: native CSS `lineClamp`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { LineClampConfig } from './LineClampConfig';

// Output type widened to CSSProperties because the legacy `-webkit-*` keys
// we emit for the line-clamp shim are not always present in csstype; see MDN:
//   https://developer.mozilla.org/docs/Web/CSS/line-clamp
export type LineClampStyles = CSSProperties;

export function applyLineClamp(config: LineClampConfig): LineClampStyles {
  const out: Record<string, string | number> = {};                 // loose accumulator
  if (config.value === undefined) return out as LineClampStyles;       // nothing to emit
  // wave-37 lane W2 — `line-clamp: auto` resolves to a COUNT before it can be
  // emitted: no shipping browser implements the `line-clamp` longhand (checked
  // on the corpus's own capture browser, Chrome 151: CSS.supports('line-clamp',
  // '3') === false), so the only lever is legacy `-webkit-line-clamp`, whose
  // grammar is `none | <integer [1,∞]>`.  The extractor did the resolution;
  // here we pick which of the three shapes that leaves us in.
  if (config.auto) {
    const n = config.autoLines;
    if (n === undefined) {
      // Unresolved constraint (percentage / calc() max-height, `line-height:
      // normal` against a px bound) — or no block-size constraint at all, in
      // which case `auto` is spec-correctly a no-op.  Emit nothing rather than
      // guess a line count; overflow stays visible, matching an unclamped box.
      return out as LineClampStyles;
    }
    if (n < 1) {
      // `max-height:0` (or a sub-line bound): zero line boxes fit.  0 is
      // outside -webkit-line-clamp's grammar, so clip with overflow instead —
      // the max-height declaration the sizing engine already emitted supplies
      // the bound, and hidden overflow makes it actually cut.
      out.overflow = 'hidden';
      return out as LineClampStyles;
    }
    // The clamp itself: cap the block size at exactly N line boxes and hide
    // what falls past it.  `${n}lh` is emitted rather than a pixel count so
    // the browser resolves the line box with the element's OWN used
    // line-height — the same number the extractor floored, without the
    // runtime having to re-derive `normal`.  This overwrites the max-height
    // the sizing engine already emitted, which is correct and never grows the
    // box: N is the floor of that very constraint expressed in lines.
    //
    // Deliberately NOT the `-webkit-box` + `-webkit-line-clamp` trio the
    // integer path uses below.  That trio does add the block ellipsis, but
    // `display:-webkit-box` re-parents block CHILDREN as box items, and 30 of
    // the corpus's 45 `line-clamp: auto` tests clamp a container with element
    // children.  MEASURED (probe fix1 vs fix2, 45 bucket-A tests): the trio
    // scored 35/45 and collapsed line-clamp-auto-046 (`contain: size` child)
    // to a BLANK capture — Chrome resolves that box to one line box, 32px,
    // and the yellow subject disappears entirely.  Geometry-only scored 36/45
    // with no such cliff.  The missing "…" costs a few px of ink on one line;
    // the wrong box costs the whole test.
    out.lineClamp = n;                                              // standards key, for engines that ship it
    out.maxHeight = `${n}lh`;                                       // clamp point: N whole line boxes
    out.overflow = 'hidden';                                        // cut what starts after the clamp point
    return out as LineClampStyles;
  }
  const v = config.value;                                             // ready string/number
  out.lineClamp = v;                                                   // native CSS property
  // Line-clamp shim: Chromium/Safari require the -webkit-box trio for the
  // clamp behaviour to activate.  We emit all four keys; modern browsers
  // prefer the standard `line-clamp` anyway so this is safe to double-stamp.
  if (v !== 'none') {                                                  // keep UA default when 'none'
    out.display = '-webkit-box';                                      // required flex-like container
    (out as Record<string, string | number>)['WebkitBoxOrient'] = 'vertical'; // required axis
    (out as Record<string, string | number>)['WebkitLineClamp'] = v as string | number; // legacy key
    out.overflow = 'hidden';                                          // required for clamp to take effect
  }
  return out as LineClampStyles;
}
