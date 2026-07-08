// _edge_shared.ts — helpers for the edge-offset properties
// (Top/Right/Bottom/Left + InsetBlockStart/End + InsetInlineStart/End).
// Every edge property shares the same IR vocabulary (/tmp/layout_ir/position-*):
//   'auto' (bare string) | number (treated as percentage per parser) |
//   { px: N } | { expr: 'calc(…)' }.
// layoutLength() handles all of those; this file just curries a type-specific
// extractor factory to keep the per-property triplets tiny.

import { foldLast, layoutLength, type IRPropertyLike } from '../_shared';

// Factory: produce an extractor for a given IR property type.  The resulting
// config exposes `value` as a CSS-ready string (or undefined).
export function edgeExtractor(type: string) {
  return (properties: IRPropertyLike[]) => ({
    value: foldLast(properties, type, layoutLength),                                // every variant is length-or-keyword
  });
}
