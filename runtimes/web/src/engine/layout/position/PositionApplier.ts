// PositionApplier.ts — emits a CSS declaration for the `position` property.
// Standard property — maps 1:1 to `CSSProperties['position']`.  Spec: CSS Positioned Layout 3 — https://developer.mozilla.org/docs/Web/CSS/position.

import type { CSSProperties } from 'react';
import type { PositionConfig } from './PositionConfig';

export type PositionStyles = Pick<CSSProperties, 'position'>;

export function applyPosition(config: PositionConfig): PositionStyles {
  if (config.value === undefined) return {};                         // unset
  return { position: config.value } as PositionStyles;                    // typed single-key
}
