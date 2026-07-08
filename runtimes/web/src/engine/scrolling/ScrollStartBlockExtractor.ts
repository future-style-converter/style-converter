// ScrollStartBlockExtractor.ts — folds IR `ScrollStartBlock` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartBlockConfig } from './ScrollStartBlockConfig';
export function extractScrollStartBlock(properties: IRPropertyLike[]): ScrollStartBlockConfig {
  return { value: foldLast(properties, 'ScrollStartBlock', keywordOrRaw) };
}
