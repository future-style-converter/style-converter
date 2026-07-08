// ColorApplier.ts — serialises a ColorConfig into the `color` CSS field.
// Reconstructs dynamic colors (color-mix, light-dark, relative) natively.

import type { CSSProperties } from 'react';
import { colorToCss } from './DynamicColorCss';
import type { ColorConfig } from './ColorConfig';

// Output shape restricted to the one field we touch.
export type ColorStyles = Pick<CSSProperties, 'color'>;

// Pure function — emit `color` only when explicitly set.
export function applyTextColor(config: ColorConfig): ColorStyles {
  const out: ColorStyles = {};                                        // blank accumulator
  if (config.color) {                                                 // only emit when parsed
    out.color = colorToCss(config.color);                             // rgba(...) or dynamic reconstruction
  }
  return out;                                                         // {} when unset
}
