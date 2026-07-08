// PositionVisibilityExtractor.ts — folds IR `PositionVisibility` properties into a PositionVisibilityConfig.
// Every variant in parsing/css/properties/longhands/layout/**/PositionVisibilityPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { PositionVisibilityConfig, POSITIONVISIBILITY_PROPERTY_TYPE } from './PositionVisibilityConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractPositionVisibility(properties: IRPropertyLike[]): PositionVisibilityConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, POSITIONVISIBILITY_PROPERTY_TYPE, kebab) };
}
