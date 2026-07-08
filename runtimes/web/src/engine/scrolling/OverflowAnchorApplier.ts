// OverflowAnchorApplier.ts — emits { overflowAnchor }.  MDN: overflow-anchor.
import type { CSSProperties } from 'react';
import type { OverflowAnchorConfig } from './OverflowAnchorConfig';
export function applyOverflowAnchor(c: OverflowAnchorConfig): CSSProperties {
  return c.value === undefined ? {} : { overflowAnchor: c.value } as CSSProperties;
}
