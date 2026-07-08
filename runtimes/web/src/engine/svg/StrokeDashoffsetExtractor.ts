// StrokeDashoffsetExtractor.ts — folds IR `StrokeDashoffset` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { StrokeDashoffsetConfig } from './StrokeDashoffsetConfig';
export function extractStrokeDashoffset(properties: IRPropertyLike[]): StrokeDashoffsetConfig {
  return { value: foldLast(properties, 'StrokeDashoffset', keywordOrRaw) };
}
