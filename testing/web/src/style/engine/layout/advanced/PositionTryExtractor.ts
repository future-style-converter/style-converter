// PositionTryExtractor.ts — joins the ident list with commas per CSS grammar.

import { PositionTryConfig, POSITION_TRY_PROPERTY_TYPE } from './PositionTryConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!Array.isArray(data)) return undefined;                                       // expect ident list
  if (data.length === 0) return 'none';                                             // parser models 'none' as []
  const names = (data as unknown[]).filter((s): s is string => typeof s === 'string');
  return names.length ? names.join(', ') : undefined;                               // CSSWG list form
}

export function extractPositionTry(properties: IRPropertyLike[]): PositionTryConfig {
  return { value: foldLast(properties, POSITION_TRY_PROPERTY_TYPE, parse) };
}
