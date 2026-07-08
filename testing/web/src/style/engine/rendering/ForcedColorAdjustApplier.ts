// ForcedColorAdjustApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/forced-color-adjust.
import type { CSSProperties } from 'react';
import type { ForcedColorAdjustConfig } from './ForcedColorAdjustConfig';
export function applyForcedColorAdjust(c: ForcedColorAdjustConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ forcedColorAdjust: c.value } as unknown as CSSProperties) as Record<string, string>;
}
