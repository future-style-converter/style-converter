// ScrollStartExtractor.ts — folds IR `ScrollStart` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartConfig } from './ScrollStartConfig';
export function extractScrollStart(properties: IRPropertyLike[]): ScrollStartConfig {
  return { value: foldLast(properties, 'ScrollStart', keywordOrRaw) };
}
