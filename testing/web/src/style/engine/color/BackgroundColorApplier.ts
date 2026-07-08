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
  if (config.color) {                                                 // only emit when set
    out.backgroundColor = colorToCss(config.color);                   // rgba(...) or dynamic reconstruction
  }
  return out;                                                         // may be {} when unset
}
