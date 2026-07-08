// StrokeApplier.ts — emits { stroke }.  MDN: stroke.
import type { CSSProperties } from 'react';
import type { StrokeConfig } from './StrokeConfig';
export function applyStroke(c: StrokeConfig): CSSProperties {
  return c.value === undefined ? {} : { stroke: c.value } as CSSProperties;
}
