// TopApplier.ts — emits `top`.  Native CSS 2 / Logical Properties 1.
// Spec: https://developer.mozilla.org/docs/Web/CSS/t-o-p.

import type { CSSProperties } from 'react';
import type { TopConfig } from './TopConfig';

export type TopStyles = Pick<CSSProperties, 'top'>;

export function applyTop(config: TopConfig): TopStyles {
  if (config.value === undefined) return {};
  return { top: config.value } as TopStyles;
}
