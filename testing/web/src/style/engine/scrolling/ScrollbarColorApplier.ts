// ScrollbarColorApplier.ts — emits { scrollbarColor }.  MDN: scrollbar-color.
import type { CSSProperties } from 'react';
import type { ScrollbarColorConfig } from './ScrollbarColorConfig';
export function applyScrollbarColor(c: ScrollbarColorConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollbarColor: c.value } as CSSProperties;
}
