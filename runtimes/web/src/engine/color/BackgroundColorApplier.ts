// BackgroundColorApplier.ts — serialises a BackgroundColorConfig to inline CSS.
// Web is easy: both static sRGB and dynamic (color-mix/light-dark/relative) are
// native CSS features so we just emit the reconstructed CSS string.

import type { CSSProperties } from 'react';
import { colorToCss } from './DynamicColorCss';
import type { BackgroundColorConfig } from './BackgroundColorConfig';

// Partial CSSProperties limited to the one field we populate.
export type BackgroundColorStyles = Pick<CSSProperties, 'backgroundColor'>;

// Pure function — no side effects, trivial to test.
export function applyBackgroundColor(config: BackgroundColorConfig): BackgroundColorStyles {
  const out: BackgroundColorStyles = {};                              // blank accumulator
  if (config.inherit) {
    // css-cascade-5 §7.3.2: hand the keyword straight to the browser rather
    // than resolving it here. The engine sees ONE component's declarations
    // and has no parent context, but the DOM does — the renderer nests each
    // component inside its slot parent, so CSS inheritance on the live tree
    // is the authoritative resolver. Crucially it inherits the parent's
    // COMPUTED value, so a parent `background-color: currentcolor` (or a
    // `color-mix()` containing it) arrives still-unresolved and re-resolves
    // against THIS element's own `color` — css-color-4 §6.4. Measured in
    // Chrome 141 on the exact three-div nesting the renderer emits:
    // outer(color:red,bg:currentcolor)=rgb(255,0,0), middle(bg:inherit)=
    // rgb(255,0,0), inner(color:green,bg:inherit)=rgb(0,128,0); and
    // color-mix(in lch, currentcolor 50%, blue) inherited into a green child
    // gives lch(37.9213 99.596 217.874), byte-identical to the reference's
    // color-mix(in lch, green 50%, blue).
    out.backgroundColor = 'inherit';
    return out;                                                       // keyword wins outright
  }
  if (config.color) {                                                 // only emit when set
    out.backgroundColor = colorToCss(config.color);                   // rgba(...) or dynamic reconstruction
  }
  return out;                                                         // may be {} when unset
}
