// ResizeExtractor.ts — folds IR `Resize` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ResizeConfig } from './ResizeConfig';
export function extractResize(properties: IRPropertyLike[]): ResizeConfig {
  return { value: foldLast(properties, 'Resize', keywordOrRaw) };
}
