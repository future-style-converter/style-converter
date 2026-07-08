// SizeExtractor.ts — folds IR `Size` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { SizeConfig } from './SizeConfig';
export function extractSize(properties: IRPropertyLike[]): SizeConfig {
  return { value: foldLast(properties, 'Size', keywordOrRaw) };
}
