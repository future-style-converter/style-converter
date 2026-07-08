// BookmarkLevelExtractor.ts — folds IR `BookmarkLevel` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BookmarkLevelConfig } from './BookmarkLevelConfig';
export function extractBookmarkLevel(properties: IRPropertyLike[]): BookmarkLevelConfig {
  return { value: foldLast(properties, 'BookmarkLevel', keywordOrRaw) };
}
