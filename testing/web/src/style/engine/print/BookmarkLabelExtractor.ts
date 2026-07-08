// BookmarkLabelExtractor.ts — folds IR `BookmarkLabel` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BookmarkLabelConfig } from './BookmarkLabelConfig';
export function extractBookmarkLabel(properties: IRPropertyLike[]): BookmarkLabelConfig {
  return { value: foldLast(properties, 'BookmarkLabel', keywordOrRaw) };
}
