// ClearApplier.ts — emits a CSS declaration for the `clear` property.
// Standard property — maps 1:1 to `CSSProperties['clear']`.  Spec: CSS 2 — https://developer.mozilla.org/docs/Web/CSS/clear.

import type { CSSProperties } from 'react';
import type { ClearConfig } from './ClearConfig';

export type ClearStyles = Pick<CSSProperties, 'clear'>;

export function applyClear(config: ClearConfig): ClearStyles {
  if (config.value === undefined) return {};                         // unset
  return { clear: config.value } as ClearStyles;                    // typed single-key
}
