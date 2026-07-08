// MarksExtractor.ts — folds IR `Marks` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { MarksConfig } from './MarksConfig';
export function extractMarks(properties: IRPropertyLike[]): MarksConfig {
  return { value: foldLast(properties, 'Marks', keywordOrRaw) };
}
