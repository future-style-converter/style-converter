// PositionTryFallbacksExtractor.ts — joins ident list with commas.

import { PositionTryFallbacksConfig, POSITION_TRY_FALLBACKS_PROPERTY_TYPE } from './PositionTryFallbacksConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!Array.isArray(data)) return undefined;
  const names = (data as unknown[]).filter((s): s is string => typeof s === 'string');
  return names.length ? names.join(', ') : undefined;
}

export function extractPositionTryFallbacks(properties: IRPropertyLike[]): PositionTryFallbacksConfig {
  return { value: foldLast(properties, POSITION_TRY_FALLBACKS_PROPERTY_TYPE, parse) };
}
