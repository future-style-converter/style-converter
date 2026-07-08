// BleedExtractor.ts — folds IR `Bleed` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BleedConfig } from './BleedConfig';
export function extractBleed(properties: IRPropertyLike[]): BleedConfig {
  return { value: foldLast(properties, 'Bleed', keywordOrRaw) };
}
