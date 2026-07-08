// ShapeRenderingExtractor.ts — folds IR `ShapeRendering` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ShapeRenderingConfig } from './ShapeRenderingConfig';
export function extractShapeRendering(properties: IRPropertyLike[]): ShapeRenderingConfig {
  return { value: foldLast(properties, 'ShapeRendering', keywordOrRaw) };
}
