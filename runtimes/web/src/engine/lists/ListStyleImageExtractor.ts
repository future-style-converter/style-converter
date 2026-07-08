// ListStyleImageExtractor.ts — folds IR `ListStyleImage` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ListStyleImageConfig } from './ListStyleImageConfig';
export function extractListStyleImage(properties: IRPropertyLike[]): ListStyleImageConfig {
  return { value: foldLast(properties, 'ListStyleImage', keywordOrRaw) };
}
