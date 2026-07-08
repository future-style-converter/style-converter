// ScrollMarginBottomApplier.ts — emits { scrollMarginBottom }.  MDN: scroll-margin-bottom.
import type { CSSProperties } from 'react';
import type { ScrollMarginBottomConfig } from './ScrollMarginBottomConfig';
export function applyScrollMarginBottom(c: ScrollMarginBottomConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginBottom: c.value } as CSSProperties;
}
