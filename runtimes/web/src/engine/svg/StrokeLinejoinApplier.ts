// StrokeLinejoinApplier.ts — emits { strokeLinejoin }.  MDN: stroke-linejoin.
import type { CSSProperties } from 'react';
import type { StrokeLinejoinConfig } from './StrokeLinejoinConfig';
export function applyStrokeLinejoin(c: StrokeLinejoinConfig): CSSProperties {
  return c.value === undefined ? {} : { strokeLinejoin: c.value } as CSSProperties;
}
