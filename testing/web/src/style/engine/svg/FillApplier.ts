// FillApplier.ts — emits { fill }.  MDN: fill.
import type { CSSProperties } from 'react';
import type { FillConfig } from './FillConfig';
export function applyFill(c: FillConfig): CSSProperties {
  return c.value === undefined ? {} : { fill: c.value } as CSSProperties;
}
