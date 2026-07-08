// StopColorApplier.ts — emits { stopColor }.  MDN: stop-color.
import type { CSSProperties } from 'react';
import type { StopColorConfig } from './StopColorConfig';
export function applyStopColor(c: StopColorConfig): CSSProperties {
  return c.value === undefined ? {} : { stopColor: c.value } as CSSProperties;
}
