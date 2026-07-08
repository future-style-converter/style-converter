// ImageOrientationExtractor.ts — folds IR `ImageOrientation` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ImageOrientationConfig } from './ImageOrientationConfig';
export function extractImageOrientation(properties: IRPropertyLike[]): ImageOrientationConfig {
  return { value: foldLast(properties, 'ImageOrientation', keywordOrRaw) };
}
