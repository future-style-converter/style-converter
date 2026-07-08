// RightApplier.ts — emits `right`.  Native CSS 2 / Logical Properties 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/r-i-g-h-t.

import type { CSSProperties } from 'react';
import type { RightConfig } from './RightConfig';

export type RightStyles = Pick<CSSProperties, 'right'>;

export function applyRight(config: RightConfig): RightStyles {
  if (config.value === undefined) return {};
  return { right: config.value } as RightStyles;
}
