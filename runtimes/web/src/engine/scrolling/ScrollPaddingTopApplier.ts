// ScrollPaddingTopApplier.ts — emits { scrollPaddingTop }.  MDN: scroll-padding-top.
import type { CSSProperties } from 'react';
import type { ScrollPaddingTopConfig } from './ScrollPaddingTopConfig';
export function applyScrollPaddingTop(c: ScrollPaddingTopConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingTop: c.value } as CSSProperties;
}
