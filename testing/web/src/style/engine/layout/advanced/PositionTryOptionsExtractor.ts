// PositionTryOptionsExtractor.ts — kebab each token, join with spaces.

import { PositionTryOptionsConfig, POSITION_TRY_OPTIONS_PROPERTY_TYPE } from './PositionTryOptionsConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!Array.isArray(data)) return undefined;
  const tokens = (data as unknown[]).map(kebab).filter((t): t is string => !!t);    // SHOUTY→kebab
  return tokens.length ? tokens.join(' ') : undefined;                              // space-separated list
}

export function extractPositionTryOptions(properties: IRPropertyLike[]): PositionTryOptionsConfig {
  return { value: foldLast(properties, POSITION_TRY_OPTIONS_PROPERTY_TYPE, parse) };
}
