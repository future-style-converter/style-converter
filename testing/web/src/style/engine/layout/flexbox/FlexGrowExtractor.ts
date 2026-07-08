// FlexGrowExtractor.ts — folds IR `FlexGrow` entries into a FlexGrowConfig.
// IR shapes observed in /tmp/layout_ir/flex-grow-shrink-basis/tmpOutput.json:
//   { value: { type: '…FlexGrowValue.Number', value: N }, normalizedValue: N }
//   N (bare number) — produced by the generic number parser
// Both flavours collapse to a single number we can hand straight to CSS.

import { FlexGrowConfig, FLEX_GROW_PROPERTY_TYPE } from './FlexGrowConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

// Shape-tolerant extractor: try normalizedValue → inner value → bare number.
function parse(data: unknown): number | string | undefined {
  if (typeof data === 'number' && Number.isFinite(data)) return data;             // bare number case
  if (data && typeof data === 'object') {                                         // normalised object
    const o = data as Record<string, unknown>;
    if (typeof o.normalizedValue === 'number') return o.normalizedValue;          // preferred: already-normalised
    const inner = o.value as Record<string, unknown> | undefined;                 // inner sealed-subclass
    if (inner && typeof inner.value === 'number') return inner.value;             // fallback: raw number field
  }
  return undefined;                                                               // unknown shape → drop
}

export function extractFlexGrow(properties: IRPropertyLike[]): FlexGrowConfig {
  return { value: foldLast(properties, FLEX_GROW_PROPERTY_TYPE, parse) };
}
