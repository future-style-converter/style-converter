// FloodOpacityExtractor.ts — folds IR `FloodOpacity` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { FloodOpacityConfig } from './FloodOpacityConfig';
export function extractFloodOpacity(properties: IRPropertyLike[]): FloodOpacityConfig {
  return { value: foldLast(properties, 'FloodOpacity', keywordOrRaw) };
}
