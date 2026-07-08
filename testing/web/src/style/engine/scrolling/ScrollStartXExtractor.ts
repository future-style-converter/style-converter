// ScrollStartXExtractor.ts — folds IR `ScrollStartX` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartXConfig } from './ScrollStartXConfig';
export function extractScrollStartX(properties: IRPropertyLike[]): ScrollStartXConfig {
  return { value: foldLast(properties, 'ScrollStartX', keywordOrRaw) };
}
