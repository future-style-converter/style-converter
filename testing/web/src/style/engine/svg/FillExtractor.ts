// FillExtractor.ts — folds IR `Fill` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { FillConfig } from './FillConfig';
export function extractFill(properties: IRPropertyLike[]): FillConfig {
  return { value: foldLast(properties, 'Fill', keywordOrRaw) };
}
