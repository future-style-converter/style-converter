// EnableBackgroundExtractor.ts — folds IR `EnableBackground` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { EnableBackgroundConfig } from './EnableBackgroundConfig';
export function extractEnableBackground(properties: IRPropertyLike[]): EnableBackgroundConfig {
  return { value: foldLast(properties, 'EnableBackground', keywordOrRaw) };
}
