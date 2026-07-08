// PageExtractor.ts — folds IR `Page` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PageConfig } from './PageConfig';
export function extractPage(properties: IRPropertyLike[]): PageConfig {
  return { value: foldLast(properties, 'Page', keywordOrRaw) };
}
