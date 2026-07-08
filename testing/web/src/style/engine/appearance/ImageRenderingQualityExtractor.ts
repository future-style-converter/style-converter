// ImageRenderingQualityExtractor.ts — folds IR `ImageRenderingQuality` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ImageRenderingQualityConfig } from './ImageRenderingQualityConfig';
export function extractImageRenderingQuality(properties: IRPropertyLike[]): ImageRenderingQualityConfig {
  return { value: foldLast(properties, 'ImageRenderingQuality', keywordOrRaw) };
}
