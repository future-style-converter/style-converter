// OverscrollBehaviorExtractor.ts — folds IR `OverscrollBehavior` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { OverscrollBehaviorConfig } from './OverscrollBehaviorConfig';
export function extractOverscrollBehavior(properties: IRPropertyLike[]): OverscrollBehaviorConfig {
  return { value: foldLast(properties, 'OverscrollBehavior', keywordOrRaw) };
}
