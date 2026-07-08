// BookmarkTargetExtractor.ts — folds IR `BookmarkTarget` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BookmarkTargetConfig } from './BookmarkTargetConfig';
export function extractBookmarkTarget(properties: IRPropertyLike[]): BookmarkTargetConfig {
  return { value: foldLast(properties, 'BookmarkTarget', keywordOrRaw) };
}
