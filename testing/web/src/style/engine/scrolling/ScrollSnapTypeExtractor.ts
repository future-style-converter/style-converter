// ScrollSnapTypeExtractor.ts — folds IR `ScrollSnapType` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollSnapTypeConfig } from './ScrollSnapTypeConfig';
export function extractScrollSnapType(properties: IRPropertyLike[]): ScrollSnapTypeConfig {
  return { value: foldLast(properties, 'ScrollSnapType', keywordOrRaw) };
}
