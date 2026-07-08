// StrokeDasharrayApplier.ts — emits { strokeDasharray }.  MDN: stroke-dasharray.
import type { CSSProperties } from 'react';
import type { StrokeDasharrayConfig } from './StrokeDasharrayConfig';
export function applyStrokeDasharray(c: StrokeDasharrayConfig): CSSProperties {
  return c.value === undefined ? {} : { strokeDasharray: c.value } as CSSProperties;
}
