// ContentVisibilityExtractor.ts — folds IR `ContentVisibility` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContentVisibilityConfig } from './ContentVisibilityConfig';
export function extractContentVisibility(properties: IRPropertyLike[]): ContentVisibilityConfig {
  return { value: foldLast(properties, 'ContentVisibility', keywordOrRaw) };
}
