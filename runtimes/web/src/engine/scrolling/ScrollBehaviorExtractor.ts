// ScrollBehaviorExtractor.ts — folds IR `ScrollBehavior` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollBehaviorConfig } from './ScrollBehaviorConfig';
export function extractScrollBehavior(properties: IRPropertyLike[]): ScrollBehaviorConfig {
  return { value: foldLast(properties, 'ScrollBehavior', keywordOrRaw) };
}
