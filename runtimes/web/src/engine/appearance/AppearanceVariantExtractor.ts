// AppearanceVariantExtractor.ts — folds IR `AppearanceVariant` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { AppearanceVariantConfig } from './AppearanceVariantConfig';
export function extractAppearanceVariant(properties: IRPropertyLike[]): AppearanceVariantConfig {
  return { value: foldLast(properties, 'AppearanceVariant', keywordOrRaw) };
}
