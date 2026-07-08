// AzimuthExtractor.ts — folds IR `Azimuth` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { AzimuthConfig } from './AzimuthConfig';
export function extractAzimuth(properties: IRPropertyLike[]): AzimuthConfig {
  return { value: foldLast(properties, 'Azimuth', keywordOrRaw) };
}
