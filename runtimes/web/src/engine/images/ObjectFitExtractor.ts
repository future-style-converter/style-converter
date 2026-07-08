// ObjectFitExtractor.ts — folds IR `ObjectFit` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ObjectFitConfig } from './ObjectFitConfig';
export function extractObjectFit(properties: IRPropertyLike[]): ObjectFitConfig {
  return { value: foldLast(properties, 'ObjectFit', keywordOrRaw) };
}
