// ScrollPaddingApplier.ts — emits { scrollPadding }.  MDN: scroll-padding.
import type { CSSProperties } from 'react';
import type { ScrollPaddingConfig } from './ScrollPaddingConfig';
export function applyScrollPadding(c: ScrollPaddingConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPadding: c.value } as CSSProperties;
}
