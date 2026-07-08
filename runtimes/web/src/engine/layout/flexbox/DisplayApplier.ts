// DisplayApplier.ts — emits a CSS declaration for the `display` property.
// Standard property — maps 1:1 to `CSSProperties['display']`.  Spec: CSS 2 / Display L3 — https://developer.mozilla.org/docs/Web/CSS/display.

import type { CSSProperties } from 'react';
import type { DisplayConfig } from './DisplayConfig';

export type DisplayStyles = Pick<CSSProperties, 'display'>;

export function applyDisplay(config: DisplayConfig): DisplayStyles {
  if (config.value === undefined) return {};                         // unset
  return { display: config.value } as DisplayStyles;                    // typed single-key
}
