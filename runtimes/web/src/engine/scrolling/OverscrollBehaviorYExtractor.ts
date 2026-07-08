// OverscrollBehaviorYExtractor.ts — folds IR `OverscrollBehaviorY` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { OverscrollBehaviorYConfig } from './OverscrollBehaviorYConfig';
export function extractOverscrollBehaviorY(properties: IRPropertyLike[]): OverscrollBehaviorYConfig {
  return { value: foldLast(properties, 'OverscrollBehaviorY', keywordOrRaw) };
}
