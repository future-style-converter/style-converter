// DynamicRangeLimitExtractor.ts — folds IR `DynamicRangeLimit` values.
// Wire shape (DynamicRangeLimitPropertyParser.kt → flattened enum string):
//   data: 'STANDARD' | 'HIGH' | 'CONSTRAINED_HIGH'
// keywordOrRaw kebab-cases the token ('CONSTRAINED_HIGH' → 'constrained-high').
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import { DYNAMIC_RANGE_LIMIT_PROPERTY_TYPE, type DynamicRangeLimitConfig } from './DynamicRangeLimitConfig';

// Single-pass fold, last write wins — matches every other engine extractor.
export function extractDynamicRangeLimit(properties: IRPropertyLike[]): DynamicRangeLimitConfig {
  return { value: foldLast(properties, DYNAMIC_RANGE_LIMIT_PROPERTY_TYPE, keywordOrRaw) };
}
