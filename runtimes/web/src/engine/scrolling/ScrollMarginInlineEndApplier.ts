// ScrollMarginInlineEndApplier.ts — emits { scrollMarginInlineEnd }.  MDN: scroll-margin-inline-end.
import type { CSSProperties } from 'react';
import type { ScrollMarginInlineEndConfig } from './ScrollMarginInlineEndConfig';
export function applyScrollMarginInlineEnd(c: ScrollMarginInlineEndConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginInlineEnd: c.value } as CSSProperties;
}
