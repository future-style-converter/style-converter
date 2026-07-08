// LeftApplier.ts — emits `left`.  Native CSS 2 / Logical Properties 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/l-e-f-t.

import type { CSSProperties } from 'react';
import type { LeftConfig } from './LeftConfig';

export type LeftStyles = Pick<CSSProperties, 'left'>;

export function applyLeft(config: LeftConfig): LeftStyles {
  if (config.value === undefined) return {};
  return { left: config.value } as LeftStyles;
}
