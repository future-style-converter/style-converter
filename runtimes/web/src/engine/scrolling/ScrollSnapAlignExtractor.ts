// ScrollSnapAlignExtractor.ts — folds IR `ScrollSnapAlign` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollSnapAlignConfig } from './ScrollSnapAlignConfig';
export function extractScrollSnapAlign(properties: IRPropertyLike[]): ScrollSnapAlignConfig {
  return { value: foldLast(properties, 'ScrollSnapAlign', keywordOrRaw) };
}
