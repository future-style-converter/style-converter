// StrokeDashoffsetApplier.ts — emits { strokeDashoffset }.  MDN: stroke-dashoffset.
import type { CSSProperties } from 'react';
import type { StrokeDashoffsetConfig } from './StrokeDashoffsetConfig';
export function applyStrokeDashoffset(c: StrokeDashoffsetConfig): CSSProperties {
  return c.value === undefined ? {} : { strokeDashoffset: c.value } as CSSProperties;
}
