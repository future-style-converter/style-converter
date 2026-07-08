// ScrollSnapStopApplier.ts — emits { scrollSnapStop }.  MDN: scroll-snap-stop.
import type { CSSProperties } from 'react';
import type { ScrollSnapStopConfig } from './ScrollSnapStopConfig';
export function applyScrollSnapStop(c: ScrollSnapStopConfig): CSSProperties {
  return c.value === undefined ? {} : { scrollSnapStop: c.value } as CSSProperties;
}
