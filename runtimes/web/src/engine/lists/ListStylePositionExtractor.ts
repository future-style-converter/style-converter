// ListStylePositionExtractor.ts — folds IR `ListStylePosition` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ListStylePositionConfig } from './ListStylePositionConfig';
export function extractListStylePosition(properties: IRPropertyLike[]): ListStylePositionConfig {
  return { value: foldLast(properties, 'ListStylePosition', keywordOrRaw) };
}
