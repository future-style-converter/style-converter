// ScrollPaddingRightApplier.ts — emits { scrollPaddingRight }.  MDN: scroll-padding-right.
import type { CSSProperties } from 'react';
import type { ScrollPaddingRightConfig } from './ScrollPaddingRightConfig';
export function applyScrollPaddingRight(c: ScrollPaddingRightConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingRight: c.value } as CSSProperties;
}
