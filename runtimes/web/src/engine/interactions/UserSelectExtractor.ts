// UserSelectExtractor.ts — folds IR `UserSelect` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { UserSelectConfig } from './UserSelectConfig';
export function extractUserSelect(properties: IRPropertyLike[]): UserSelectConfig {
  return { value: foldLast(properties, 'UserSelect', keywordOrRaw) };
}
