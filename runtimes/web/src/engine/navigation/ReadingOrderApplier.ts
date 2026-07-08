// ReadingOrderApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/reading-order.
import type { CSSProperties } from 'react';
import type { ReadingOrderConfig } from './ReadingOrderConfig';
export function applyReadingOrder(c: ReadingOrderConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ readingOrder: c.value } as unknown as CSSProperties) as Record<string, string>;
}
