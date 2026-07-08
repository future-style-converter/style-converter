// OffsetAnchorApplier.ts — emits `offset-anchor`.  Native.
// Spec: https://developer.mozilla.org/docs/Web/CSS/offset-anchor.

import type { CSSProperties } from 'react';
import type { OffsetAnchorConfig } from './OffsetAnchorConfig';

export type OffsetAnchorStyles = Pick<CSSProperties, 'offsetAnchor'>;

export function applyOffsetAnchor(config: OffsetAnchorConfig): OffsetAnchorStyles {
  if (config.value === undefined) return {};
  return { offsetAnchor: config.value } as OffsetAnchorStyles;
}
