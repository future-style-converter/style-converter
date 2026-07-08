// ScrollPaddingBlockApplier.ts — emits { scrollPaddingBlock }.  MDN: scroll-padding-block.
import type { CSSProperties } from 'react';
import type { ScrollPaddingBlockConfig } from './ScrollPaddingBlockConfig';
export function applyScrollPaddingBlock(c: ScrollPaddingBlockConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingBlock: c.value } as CSSProperties;
}
