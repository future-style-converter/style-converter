// ScrollMarginBlockEndApplier.ts — emits { scrollMarginBlockEnd }.  MDN: scroll-margin-block-end.
import type { CSSProperties } from 'react';
import type { ScrollMarginBlockEndConfig } from './ScrollMarginBlockEndConfig';
export function applyScrollMarginBlockEnd(c: ScrollMarginBlockEndConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginBlockEnd: c.value } as CSSProperties;
}
