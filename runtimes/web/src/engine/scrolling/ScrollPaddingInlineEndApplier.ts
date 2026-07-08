// ScrollPaddingInlineEndApplier.ts — emits { scrollPaddingInlineEnd }.  MDN: scroll-padding-inline-end.
import type { CSSProperties } from 'react';
import type { ScrollPaddingInlineEndConfig } from './ScrollPaddingInlineEndConfig';
export function applyScrollPaddingInlineEnd(c: ScrollPaddingInlineEndConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingInlineEnd: c.value } as CSSProperties;
}
