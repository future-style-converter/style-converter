// CursorExtractor.ts — folds IR `Cursor` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { CursorConfig } from './CursorConfig';
export function extractCursor(properties: IRPropertyLike[]): CursorConfig {
  return { value: foldLast(properties, 'Cursor', keywordOrRaw) };
}
