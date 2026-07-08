// RegionFragmentApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/region-fragment.
import type { CSSProperties } from 'react';
import type { RegionFragmentConfig } from './RegionFragmentConfig';
export function applyRegionFragment(c: RegionFragmentConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ regionFragment: c.value } as unknown as CSSProperties) as Record<string, string>;
}
