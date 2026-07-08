// ScrollMarginBlockStartApplier.ts — emits { scrollMarginBlockStart }.  MDN: scroll-margin-block-start.
import type { CSSProperties } from 'react';
import type { ScrollMarginBlockStartConfig } from './ScrollMarginBlockStartConfig';
export function applyScrollMarginBlockStart(c: ScrollMarginBlockStartConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginBlockStart: c.value } as CSSProperties;
}
