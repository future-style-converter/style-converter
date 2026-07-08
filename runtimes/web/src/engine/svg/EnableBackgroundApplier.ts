// EnableBackgroundApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/enable-background.
import type { CSSProperties } from 'react';
import type { EnableBackgroundConfig } from './EnableBackgroundConfig';
export function applyEnableBackground(c: EnableBackgroundConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ enableBackground: c.value } as unknown as CSSProperties) as Record<string, string>;
}
