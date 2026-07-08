// ScrollMarginApplier.ts — emits { scrollMargin }.  MDN: scroll-margin.
import type { CSSProperties } from 'react';
import type { ScrollMarginConfig } from './ScrollMarginConfig';
export function applyScrollMargin(c: ScrollMarginConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMargin: c.value } as CSSProperties;
}
