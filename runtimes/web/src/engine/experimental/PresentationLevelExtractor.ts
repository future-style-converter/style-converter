// PresentationLevelExtractor.ts — folds IR `PresentationLevel` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { PresentationLevelConfig } from './PresentationLevelConfig';
export function extractPresentationLevel(properties: IRPropertyLike[]): PresentationLevelConfig {
  return { value: foldLast(properties, 'PresentationLevel', keywordOrRaw) };
}
