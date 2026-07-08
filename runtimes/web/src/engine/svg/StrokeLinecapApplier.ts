// StrokeLinecapApplier.ts — emits { strokeLinecap }.  MDN: stroke-linecap.
import type { CSSProperties } from 'react';
import type { StrokeLinecapConfig } from './StrokeLinecapConfig';
export function applyStrokeLinecap(c: StrokeLinecapConfig): CSSProperties {
  return c.value === undefined ? {} : { strokeLinecap: c.value } as CSSProperties;
}
