// NavLeftExtractor.ts — folds IR `NavLeft` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { NavLeftConfig } from './NavLeftConfig';
export function extractNavLeft(properties: IRPropertyLike[]): NavLeftConfig {
  return { value: foldLast(properties, 'NavLeft', keywordOrRaw) };
}
