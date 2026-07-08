// ScrollPaddingBottomApplier.ts — emits { scrollPaddingBottom }.  MDN: scroll-padding-bottom.
import type { CSSProperties } from 'react';
import type { ScrollPaddingBottomConfig } from './ScrollPaddingBottomConfig';
export function applyScrollPaddingBottom(c: ScrollPaddingBottomConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingBottom: c.value } as CSSProperties;
}
