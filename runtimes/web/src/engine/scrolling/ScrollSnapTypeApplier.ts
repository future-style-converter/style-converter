// ScrollSnapTypeApplier.ts — emits { scrollSnapType }.  MDN: scroll-snap-type.
import type { CSSProperties } from 'react';
import type { ScrollSnapTypeConfig } from './ScrollSnapTypeConfig';
export function applyScrollSnapType(c: ScrollSnapTypeConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollSnapType: c.value } as CSSProperties;
}
