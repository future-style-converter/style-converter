// StrokeWidthApplier.ts — emits { strokeWidth }.  MDN: stroke-width.
import type { CSSProperties } from 'react';
import type { StrokeWidthConfig } from './StrokeWidthConfig';
export function applyStrokeWidth(c: StrokeWidthConfig): CSSProperties {
  return c.value === undefined ? {} : { strokeWidth: c.value } as CSSProperties;
}
