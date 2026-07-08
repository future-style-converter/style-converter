// ScrollMarginRightApplier.ts — emits { scrollMarginRight }.  MDN: scroll-margin-right.
import type { CSSProperties } from 'react';
import type { ScrollMarginRightConfig } from './ScrollMarginRightConfig';
export function applyScrollMarginRight(c: ScrollMarginRightConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginRight: c.value } as CSSProperties;
}
