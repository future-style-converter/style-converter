// ScrollPaddingInlineApplier.ts — emits { scrollPaddingInline }.  MDN: scroll-padding-inline.
import type { CSSProperties } from 'react';
import type { ScrollPaddingInlineConfig } from './ScrollPaddingInlineConfig';
export function applyScrollPaddingInline(c: ScrollPaddingInlineConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingInline: c.value } as CSSProperties;
}
