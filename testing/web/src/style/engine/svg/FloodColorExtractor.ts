// FloodColorExtractor.ts — folds IR `FloodColor` values via shared colorOrKeyword.
import { foldLast, colorOrKeyword, type IRPropertyLike } from '../_phase10_shared';
import type { FloodColorConfig } from './FloodColorConfig';
export function extractFloodColor(properties: IRPropertyLike[]): FloodColorConfig {
  return { value: foldLast(properties, 'FloodColor', colorOrKeyword) };
}
