// FilterExtractor.ts — IR -> FilterConfig using shared serialiser.
import { foldLast, type IRPropertyLike } from '../_shared';
import { filterValueToCss } from './_filter_shared';
import type { FilterConfig } from './FilterConfig';
import { FILTER_PROPERTY_TYPE } from './FilterConfig';

export function extractFilter(properties: IRPropertyLike[]): FilterConfig {
  return { value: foldLast(properties, FILTER_PROPERTY_TYPE, filterValueToCss) };
}
