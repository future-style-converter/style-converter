// ScrollPaddingInlineStartApplier.ts — emits { scrollPaddingInlineStart }.  MDN: scroll-padding-inline-start.
import type { CSSProperties } from 'react';
import type { ScrollPaddingInlineStartConfig } from './ScrollPaddingInlineStartConfig';
export function applyScrollPaddingInlineStart(c: ScrollPaddingInlineStartConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollPaddingInlineStart: c.value } as CSSProperties;
}
