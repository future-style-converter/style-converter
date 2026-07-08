// OverscrollBehaviorXExtractor.ts — folds IR `OverscrollBehaviorX` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { OverscrollBehaviorXConfig } from './OverscrollBehaviorXConfig';
export function extractOverscrollBehaviorX(properties: IRPropertyLike[]): OverscrollBehaviorXConfig {
  return { value: foldLast(properties, 'OverscrollBehaviorX', keywordOrRaw) };
}
