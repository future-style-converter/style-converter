// ScrollStartTargetXExtractor.ts — folds IR `ScrollStartTargetX` values.
// Wire shape (ScrollStartTargetXPropertyParser.kt → flattened enum string):
//   data: 'NONE' | 'AUTO'  → kebab-cased by the shared keywordOrRaw helper.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ScrollStartTargetXConfig } from './ScrollStartTargetXConfig';
export function extractScrollStartTargetX(properties: IRPropertyLike[]): ScrollStartTargetXConfig {
  return { value: foldLast(properties, 'ScrollStartTargetX', keywordOrRaw) };
}
