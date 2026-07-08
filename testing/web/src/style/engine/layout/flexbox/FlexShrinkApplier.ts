// FlexShrinkApplier.ts — emits `flex-shrink`.  Native CSS.
// Spec: CSS Flexbox 1 — https://developer.mozilla.org/docs/Web/CSS/flex-shrink.

import type { CSSProperties } from 'react';
import type { FlexShrinkConfig } from './FlexShrinkConfig';

export type FlexShrinkStyles = Pick<CSSProperties, 'flexShrink'>;

export function applyFlexShrink(config: FlexShrinkConfig): FlexShrinkStyles {
  if (config.value === undefined) return {};                                      // unset → empty
  return { flexShrink: config.value } as FlexShrinkStyles;                        // native passthrough
}
