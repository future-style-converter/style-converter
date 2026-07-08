// ListStyleTypeExtractor.ts — folds IR `ListStyleType` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ListStyleTypeConfig } from './ListStyleTypeConfig';
export function extractListStyleType(properties: IRPropertyLike[]): ListStyleTypeConfig {
  return { value: foldLast(properties, 'ListStyleType', keywordOrRaw) };
}
