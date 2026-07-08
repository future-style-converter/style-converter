// FlexShrinkExtractor.ts — folds IR `FlexShrink` into a FlexShrinkConfig.
// IR shape mirrors FlexGrow exactly; we reuse the parse helper semantics.

import { FlexShrinkConfig, FLEX_SHRINK_PROPERTY_TYPE } from './FlexShrinkConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): number | string | undefined {
  if (typeof data === 'number' && Number.isFinite(data)) return data;             // bare number fast path
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (typeof o.normalizedValue === 'number') return o.normalizedValue;          // preferred: normalised
    const inner = o.value as Record<string, unknown> | undefined;
    if (inner && typeof inner.value === 'number') return inner.value;             // fallback: inner.value
  }
  return undefined;                                                               // drop unknown
}

export function extractFlexShrink(properties: IRPropertyLike[]): FlexShrinkConfig {
  return { value: foldLast(properties, FLEX_SHRINK_PROPERTY_TYPE, parse) };
}
