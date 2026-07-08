// OffsetAnchorExtractor.ts — emit 'auto' or a position pair per spec grammar.

import { OffsetAnchorConfig, OFFSET_ANCHOR_PROPERTY_TYPE } from './OffsetAnchorConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { positionPair } from './_offset_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';                                             // explicit keyword
  if (o.type === 'position') return positionPair(data);                             // two-axis
  return undefined;
}

export function extractOffsetAnchor(properties: IRPropertyLike[]): OffsetAnchorConfig {
  return { value: foldLast(properties, OFFSET_ANCHOR_PROPERTY_TYPE, parse) };
}
