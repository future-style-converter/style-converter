// MarkerSideExtractor.ts — folds IR `MarkerSide` values via shared keywordOrRaw.
// Wire shape (MarkerSidePropertyParser.kt → flattened enum string):
//   data: 'MATCH' | 'LEFT' | 'RIGHT' | 'LEFT_RIGHT'  → kebab-cased by keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import { MARKER_SIDE_PROPERTY_TYPE, type MarkerSideConfig } from './MarkerSideConfig';

// Single-pass fold, last write wins — matches the other svg extractors.
export function extractMarkerSide(properties: IRPropertyLike[]): MarkerSideConfig {
  return { value: foldLast(properties, MARKER_SIDE_PROPERTY_TYPE, keywordOrRaw) };
}
