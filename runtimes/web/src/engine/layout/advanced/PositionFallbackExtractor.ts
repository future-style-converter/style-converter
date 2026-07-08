// PositionFallbackExtractor.ts — emit `none` or the named fallback ident.

import { PositionFallbackConfig, POSITION_FALLBACK_PROPERTY_TYPE } from './PositionFallbackConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';                                             // explicit none
  if (o.type === 'named' && typeof o.name === 'string') return o.name;              // ident passthrough
  return undefined;
}

export function extractPositionFallback(properties: IRPropertyLike[]): PositionFallbackConfig {
  return { value: foldLast(properties, POSITION_FALLBACK_PROPERTY_TYPE, parse) };
}
