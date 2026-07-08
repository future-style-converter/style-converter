// BottomApplier.ts — emits `bottom`.  Native CSS 2 / Logical Properties 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/b-o-t-t-o-m.

import type { CSSProperties } from 'react';
import type { BottomConfig } from './BottomConfig';

export type BottomStyles = Pick<CSSProperties, 'bottom'>;

export function applyBottom(config: BottomConfig): BottomStyles {
  if (config.value === undefined) return {};
  return { bottom: config.value } as BottomStyles;
}
