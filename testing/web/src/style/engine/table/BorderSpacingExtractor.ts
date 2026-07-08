// BorderSpacingExtractor.ts — folds IR `BorderSpacing` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { BorderSpacingConfig } from './BorderSpacingConfig';
export function extractBorderSpacing(properties: IRPropertyLike[]): BorderSpacingConfig {
  return { value: foldLast(properties, 'BorderSpacing', keywordOrRaw) };
}
