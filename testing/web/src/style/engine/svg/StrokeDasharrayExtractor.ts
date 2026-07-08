// StrokeDasharrayExtractor.ts — folds IR `StrokeDasharray` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StrokeDasharrayConfig } from './StrokeDasharrayConfig';
export function extractStrokeDasharray(properties: IRPropertyLike[]): StrokeDasharrayConfig {
  return { value: foldLast(properties, 'StrokeDasharray', keywordOrRaw) };
}
