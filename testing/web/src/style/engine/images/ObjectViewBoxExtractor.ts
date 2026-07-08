// ObjectViewBoxExtractor.ts — folds IR `ObjectViewBox` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ObjectViewBoxConfig } from './ObjectViewBoxConfig';
export function extractObjectViewBox(properties: IRPropertyLike[]): ObjectViewBoxConfig {
  return { value: foldLast(properties, 'ObjectViewBox', keywordOrRaw) };
}
