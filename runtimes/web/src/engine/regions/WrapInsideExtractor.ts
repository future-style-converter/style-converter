// WrapInsideExtractor.ts — folds IR `WrapInside` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { WrapInsideConfig } from './WrapInsideConfig';
export function extractWrapInside(properties: IRPropertyLike[]): WrapInsideConfig {
  return { value: foldLast(properties, 'WrapInside', keywordOrRaw) };
}
