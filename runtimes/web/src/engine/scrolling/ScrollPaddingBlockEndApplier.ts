// ScrollPaddingBlockEndApplier.ts — emits { scrollPaddingBlockEnd }.  MDN: scroll-padding-block-end.
import type { CSSProperties } from 'react';
import type { ScrollPaddingBlockEndConfig } from './ScrollPaddingBlockEndConfig';
export function applyScrollPaddingBlockEnd(c: ScrollPaddingBlockEndConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingBlockEnd: c.value } as CSSProperties;
}
