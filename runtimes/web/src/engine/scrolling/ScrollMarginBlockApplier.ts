// ScrollMarginBlockApplier.ts — emits { scrollMarginBlock }.  MDN: scroll-margin-block.
import type { CSSProperties } from 'react';
import type { ScrollMarginBlockConfig } from './ScrollMarginBlockConfig';
export function applyScrollMarginBlock(c: ScrollMarginBlockConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollMarginBlock: c.value } as CSSProperties;
}
