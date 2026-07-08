// BackdropFilterExtractor.ts — same grammar as Filter.
import { foldLast, type IRPropertyLike } from '../_shared';
import { filterValueToCss } from './_filter_shared';
import type { BackdropFilterConfig } from './BackdropFilterConfig';
import { BACKDROP_FILTER_PROPERTY_TYPE } from './BackdropFilterConfig';

export function extractBackdropFilter(properties: IRPropertyLike[]): BackdropFilterConfig {
  return { value: foldLast(properties, BACKDROP_FILTER_PROPERTY_TYPE, filterValueToCss) };
}
