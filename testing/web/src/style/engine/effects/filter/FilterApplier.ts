// FilterApplier.ts — native CSS `filter` key.
import type { CSSProperties } from 'react';
import type { FilterConfig } from './FilterConfig';

export type FilterStyles = Pick<CSSProperties, 'filter'>;

export function applyFilter(config: FilterConfig): FilterStyles {
  if (config.value === undefined) return {};
  return { filter: config.value };
}
