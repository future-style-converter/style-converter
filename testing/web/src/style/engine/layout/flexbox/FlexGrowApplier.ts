// FlexGrowApplier.ts — emits `flex-grow`.  Native CSS property, no widening.
// Spec: CSS Flexbox 1 — https://developer.mozilla.org/docs/Web/CSS/flex-grow.

import type { CSSProperties } from 'react';
import type { FlexGrowConfig } from './FlexGrowConfig';

export type FlexGrowStyles = Pick<CSSProperties, 'flexGrow'>;

export function applyFlexGrow(config: FlexGrowConfig): FlexGrowStyles {
  if (config.value === undefined) return {};                                      // unset
  return { flexGrow: config.value } as FlexGrowStyles;                            // pass through
}
