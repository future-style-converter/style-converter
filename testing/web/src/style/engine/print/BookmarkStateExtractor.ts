// BookmarkStateExtractor.ts — folds IR `BookmarkState` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BookmarkStateConfig } from './BookmarkStateConfig';
export function extractBookmarkState(properties: IRPropertyLike[]): BookmarkStateConfig {
  return { value: foldLast(properties, 'BookmarkState', keywordOrRaw) };
}
