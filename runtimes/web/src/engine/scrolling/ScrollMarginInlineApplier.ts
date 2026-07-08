// ScrollMarginInlineApplier.ts — emits { scrollMarginInline }.  MDN: scroll-margin-inline.
import type { CSSProperties } from 'react';
import type { ScrollMarginInlineConfig } from './ScrollMarginInlineConfig';
export function applyScrollMarginInline(c: ScrollMarginInlineConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginInline: c.value } as CSSProperties;
}
