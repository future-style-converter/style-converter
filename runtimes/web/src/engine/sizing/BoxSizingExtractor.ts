// BoxSizingExtractor.ts — folds IR `BoxSizing` properties into a BoxSizingConfig.
// Wire shape (BoxSizingPropertyParser.kt → single-field data class, flattened):
//   data: 'CONTENT_BOX' | 'BORDER_BOX'   (bare SHOUTY enum string)
import { foldLast, type IRPropertyLike } from '../_phase10_shared';
import { BOX_SIZING_PROPERTY_TYPE, type BoxSizingConfig } from './BoxSizingConfig';

// Map the two legal enum tokens; anything else drops (parser rejects the rest).
function parse(data: unknown): BoxSizingConfig['value'] | undefined {
  if (data === 'CONTENT_BOX') return 'content-box';                 // css-sizing-3 initial value
  if (data === 'BORDER_BOX') return 'border-box';                   // border+padding included in size
  return undefined;                                                 // unknown shape → skip
}

// Single-pass fold, last write wins — matches every other engine extractor.
export function extractBoxSizing(properties: IRPropertyLike[]): BoxSizingConfig {
  return { value: foldLast(properties, BOX_SIZING_PROPERTY_TYPE, parse) };
}
