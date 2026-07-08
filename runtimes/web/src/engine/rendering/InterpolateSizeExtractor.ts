// InterpolateSizeExtractor.ts — folds IR `InterpolateSize` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { InterpolateSizeConfig } from './InterpolateSizeConfig';
export function extractInterpolateSize(properties: IRPropertyLike[]): InterpolateSizeConfig {
  return { value: foldLast(properties, 'InterpolateSize', keywordOrRaw) };
}
