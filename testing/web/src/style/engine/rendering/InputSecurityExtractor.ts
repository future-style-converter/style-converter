// InputSecurityExtractor.ts — folds IR `InputSecurity` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { InputSecurityConfig } from './InputSecurityConfig';
export function extractInputSecurity(properties: IRPropertyLike[]): InputSecurityConfig {
  return { value: foldLast(properties, 'InputSecurity', keywordOrRaw) };
}
