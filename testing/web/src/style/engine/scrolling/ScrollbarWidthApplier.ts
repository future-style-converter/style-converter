// ScrollbarWidthApplier.ts — emits { scrollbarWidth }.  MDN: scrollbar-width.
import type { CSSProperties } from 'react';
import type { ScrollbarWidthConfig } from './ScrollbarWidthConfig';
export function applyScrollbarWidth(c: ScrollbarWidthConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollbarWidth: c.value } as CSSProperties;
}
