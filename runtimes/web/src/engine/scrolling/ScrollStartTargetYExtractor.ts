// ScrollStartTargetYExtractor.ts — folds IR `ScrollStartTargetY` values.
// Wire shape (ScrollStartTargetYPropertyParser.kt → flattened enum string):
//   data: 'NONE' | 'AUTO'  → kebab-cased by the shared keywordOrRaw helper.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartTargetYConfig } from './ScrollStartTargetYConfig';
export function extractScrollStartTargetY(properties: IRPropertyLike[]): ScrollStartTargetYConfig {
  return { value: foldLast(properties, 'ScrollStartTargetY', keywordOrRaw) };
}
