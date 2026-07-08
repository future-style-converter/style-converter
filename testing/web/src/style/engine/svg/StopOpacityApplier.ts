// StopOpacityApplier.ts — emits { stopOpacity }.  MDN: stop-opacity.
import type { CSSProperties } from 'react';
import type { StopOpacityConfig } from './StopOpacityConfig';
export function applyStopOpacity(c: StopOpacityConfig): CSSProperties {
  return c.value === undefined ? {} : { stopOpacity: c.value } as CSSProperties;
}
