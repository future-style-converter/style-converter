// ScrollPaddingLeftApplier.ts — emits { scrollPaddingLeft }.  MDN: scroll-padding-left.
import type { CSSProperties } from 'react';
import type { ScrollPaddingLeftConfig } from './ScrollPaddingLeftConfig';
export function applyScrollPaddingLeft(c: ScrollPaddingLeftConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingLeft: c.value } as CSSProperties;
}
