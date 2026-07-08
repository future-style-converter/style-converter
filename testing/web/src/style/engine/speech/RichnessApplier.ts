// RichnessApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/richness.
import type { CSSProperties } from 'react';
import type { RichnessConfig } from './RichnessConfig';
export function applyRichness(c: RichnessConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ richness: c.value } as unknown as CSSProperties) as Record<string, string>;
}
