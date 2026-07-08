// BleedApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/bleed.
import type { CSSProperties } from 'react';
import type { BleedConfig } from './BleedConfig';
export function applyBleed(c: BleedConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ bleed: c.value } as unknown as CSSProperties) as Record<string, string>;
}
