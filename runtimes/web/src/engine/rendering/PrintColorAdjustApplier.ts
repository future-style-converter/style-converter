// PrintColorAdjustApplier.ts — emits { printColorAdjust }.  MDN: print-color-adjust.
import type { CSSProperties } from 'react';
import type { PrintColorAdjustConfig } from './PrintColorAdjustConfig';
export function applyPrintColorAdjust(c: PrintColorAdjustConfig): CSSProperties {
  return c.value === undefined ? {} : { printColorAdjust: c.value } as CSSProperties;
}
