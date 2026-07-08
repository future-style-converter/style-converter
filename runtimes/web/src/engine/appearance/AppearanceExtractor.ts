// AppearanceExtractor.ts — folds IR `Appearance` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { AppearanceConfig } from './AppearanceConfig';
export function extractAppearance(properties: IRPropertyLike[]): AppearanceConfig {
  return { value: foldLast(properties, 'Appearance', keywordOrRaw) };
}
