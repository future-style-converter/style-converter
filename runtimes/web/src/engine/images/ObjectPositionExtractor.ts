// ObjectPositionExtractor.ts — folds IR `ObjectPosition` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ObjectPositionConfig } from './ObjectPositionConfig';
export function extractObjectPosition(properties: IRPropertyLike[]): ObjectPositionConfig {
  return { value: foldLast(properties, 'ObjectPosition', keywordOrRaw) };
}
