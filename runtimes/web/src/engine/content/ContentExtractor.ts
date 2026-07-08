// ContentExtractor.ts — folds IR `Content` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContentConfig } from './ContentConfig';
export function extractContent(properties: IRPropertyLike[]): ContentConfig {
  return { value: foldLast(properties, 'Content', keywordOrRaw) };
}
