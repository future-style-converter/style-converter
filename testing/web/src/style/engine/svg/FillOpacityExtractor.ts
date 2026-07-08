// FillOpacityExtractor.ts — folds IR `FillOpacity` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { FillOpacityConfig } from './FillOpacityConfig';
export function extractFillOpacity(properties: IRPropertyLike[]): FillOpacityConfig {
  return { value: foldLast(properties, 'FillOpacity', keywordOrRaw) };
}
