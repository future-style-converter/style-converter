// FootnoteDisplayExtractor.ts — folds IR `FootnoteDisplay` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { FootnoteDisplayConfig } from './FootnoteDisplayConfig';
export function extractFootnoteDisplay(properties: IRPropertyLike[]): FootnoteDisplayConfig {
  return { value: foldLast(properties, 'FootnoteDisplay', keywordOrRaw) };
}
