// OverflowClipMarginApplier.ts — emits { overflowClipMargin }.  MDN: overflow-clip-margin.
import type { CSSProperties } from 'react';
import type { OverflowClipMarginConfig } from './OverflowClipMarginConfig';
export function applyOverflowClipMargin(c: OverflowClipMarginConfig): CSSProperties {
  return c.value === undefined ? {} : { overflowClipMargin: c.value } as CSSProperties;
}
