// ScrollSnapAlignApplier.ts — emits { scrollSnapAlign }.  MDN: scroll-snap-align.
import type { CSSProperties } from 'react';
import type { ScrollSnapAlignConfig } from './ScrollSnapAlignConfig';
export function applyScrollSnapAlign(c: ScrollSnapAlignConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollSnapAlign: c.value } as CSSProperties;
}
