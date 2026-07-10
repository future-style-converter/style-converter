// BoxOrientExtractor.ts — folds IR `BoxOrient` values via shared keywordOrRaw.
// Wire shape (BoxOrientPropertyParser.kt → flattened enum string):
//   data: 'HORIZONTAL' | 'VERTICAL' | 'INLINE_AXIS' | 'BLOCK_AXIS'
// keywordOrRaw kebab-cases the token ('INLINE_AXIS' → 'inline-axis').
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../../_phase10_shared';
import { BOX_ORIENT_PROPERTY_TYPE, type BoxOrientConfig } from './BoxOrientConfig';

// Single-pass fold, last write wins — matches the other flexbox extractors.
export function extractBoxOrient(properties: IRPropertyLike[]): BoxOrientConfig {
  return { value: foldLast(properties, BOX_ORIENT_PROPERTY_TYPE, keywordOrRaw) };
}
