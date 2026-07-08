// ScrollMarginLeftApplier.ts — emits { scrollMarginLeft }.  MDN: scroll-margin-left.
import type { CSSProperties } from 'react';
import type { ScrollMarginLeftConfig } from './ScrollMarginLeftConfig';
export function applyScrollMarginLeft(c: ScrollMarginLeftConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginLeft: c.value } as CSSProperties;
}
