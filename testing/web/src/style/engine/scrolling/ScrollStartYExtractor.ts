// ScrollStartYExtractor.ts — folds IR `ScrollStartY` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartYConfig } from './ScrollStartYConfig';
export function extractScrollStartY(properties: IRPropertyLike[]): ScrollStartYConfig {
  return { value: foldLast(properties, 'ScrollStartY', keywordOrRaw) };
}
