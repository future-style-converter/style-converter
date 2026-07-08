// ScrollMarginInlineStartApplier.ts — emits { scrollMarginInlineStart }.  MDN: scroll-margin-inline-start.
import type { CSSProperties } from 'react';
import type { ScrollMarginInlineStartConfig } from './ScrollMarginInlineStartConfig';
export function applyScrollMarginInlineStart(c: ScrollMarginInlineStartConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginInlineStart: c.value } as CSSProperties;
}
