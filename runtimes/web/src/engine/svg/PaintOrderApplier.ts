// PaintOrderApplier.ts — emits { paintOrder }.  MDN: paint-order.
import type { CSSProperties } from 'react';
import type { PaintOrderConfig } from './PaintOrderConfig';
export function applyPaintOrder(c: PaintOrderConfig): CSSProperties {
  return c.value === undefined ? {} : { paintOrder: c.value } as CSSProperties;
}
