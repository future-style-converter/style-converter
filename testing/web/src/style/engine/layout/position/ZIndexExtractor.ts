// ZIndexExtractor.ts — distinguishes 'auto' from integer via the `original` hint.

import { ZIndexConfig, Z_INDEX_PROPERTY_TYPE } from './ZIndexConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): number | 'auto' | undefined {
  // Parser emits { value:0, original:'auto' } for the `auto` keyword so normalised
  // platforms can still render something; preserve the keyword on web.
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (o.original === 'auto') return 'auto';                                       // explicit keyword
    if (typeof o.value === 'number' && Number.isFinite(o.value)) return Math.round(o.value);
  }
  if (typeof data === 'number' && Number.isFinite(data)) return Math.round(data);   // bare number fallback
  return undefined;
}

export function extractZIndex(properties: IRPropertyLike[]): ZIndexConfig {
  return { value: foldLast(properties, Z_INDEX_PROPERTY_TYPE, parse) };
}
