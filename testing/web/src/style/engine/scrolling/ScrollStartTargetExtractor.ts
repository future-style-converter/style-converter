// ScrollStartTargetExtractor.ts — folds IR `ScrollStartTarget` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartTargetConfig } from './ScrollStartTargetConfig';
export function extractScrollStartTarget(properties: IRPropertyLike[]): ScrollStartTargetConfig {
  return { value: foldLast(properties, 'ScrollStartTarget', keywordOrRaw) };
}
