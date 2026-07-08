// RichnessExtractor.ts — folds IR `Richness` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { RichnessConfig } from './RichnessConfig';
export function extractRichness(properties: IRPropertyLike[]): RichnessConfig {
  return { value: foldLast(properties, 'Richness', keywordOrRaw) };
}
