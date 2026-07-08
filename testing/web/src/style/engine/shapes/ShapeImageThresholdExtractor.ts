// ShapeImageThresholdExtractor.ts — folds IR `ShapeImageThreshold` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ShapeImageThresholdConfig } from './ShapeImageThresholdConfig';
export function extractShapeImageThreshold(properties: IRPropertyLike[]): ShapeImageThresholdConfig {
  return { value: foldLast(properties, 'ShapeImageThreshold', keywordOrRaw) };
}
