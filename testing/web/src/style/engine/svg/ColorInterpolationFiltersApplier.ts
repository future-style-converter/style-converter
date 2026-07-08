// ColorInterpolationFiltersApplier.ts — emits { colorInterpolationFilters }.  MDN: color-interpolation-filters.
import type { CSSProperties } from 'react';
import type { ColorInterpolationFiltersConfig } from './ColorInterpolationFiltersConfig';
export function applyColorInterpolationFilters(c: ColorInterpolationFiltersConfig): CSSProperties {
  return c.value === undefined ? {} : { colorInterpolationFilters: c.value } as CSSProperties;
}
