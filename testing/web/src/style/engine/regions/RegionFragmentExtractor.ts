// RegionFragmentExtractor.ts — folds IR `RegionFragment` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { RegionFragmentConfig } from './RegionFragmentConfig';
export function extractRegionFragment(properties: IRPropertyLike[]): RegionFragmentConfig {
  return { value: foldLast(properties, 'RegionFragment', keywordOrRaw) };
}
