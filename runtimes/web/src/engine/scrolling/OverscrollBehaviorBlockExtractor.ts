// OverscrollBehaviorBlockExtractor.ts — folds IR `OverscrollBehaviorBlock` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { OverscrollBehaviorBlockConfig } from './OverscrollBehaviorBlockConfig';
export function extractOverscrollBehaviorBlock(properties: IRPropertyLike[]): OverscrollBehaviorBlockConfig {
  return { value: foldLast(properties, 'OverscrollBehaviorBlock', keywordOrRaw) };
}
