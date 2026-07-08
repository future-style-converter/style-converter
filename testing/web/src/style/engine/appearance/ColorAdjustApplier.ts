// ColorAdjustApplier.ts — emits { colorAdjust }.  MDN: color-adjust.
import type { CSSProperties } from 'react';
import type { ColorAdjustConfig } from './ColorAdjustConfig';
export function applyColorAdjust(c: ColorAdjustConfig): CSSProperties {
  return c.value === undefined ? {} : { colorAdjust: c.value } as CSSProperties;
}
