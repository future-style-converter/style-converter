// ScrollPaddingBlockStartApplier.ts — emits { scrollPaddingBlockStart }.  MDN: scroll-padding-block-start.
import type { CSSProperties } from 'react';
import type { ScrollPaddingBlockStartConfig } from './ScrollPaddingBlockStartConfig';
export function applyScrollPaddingBlockStart(c: ScrollPaddingBlockStartConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingBlockStart: c.value } as CSSProperties;
}
