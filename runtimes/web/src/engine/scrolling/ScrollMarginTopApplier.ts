// ScrollMarginTopApplier.ts — emits { scrollMarginTop }.  MDN: scroll-margin-top.
import type { CSSProperties } from 'react';
import type { ScrollMarginTopConfig } from './ScrollMarginTopConfig';
export function applyScrollMarginTop(c: ScrollMarginTopConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginTop: c.value } as CSSProperties;
}
