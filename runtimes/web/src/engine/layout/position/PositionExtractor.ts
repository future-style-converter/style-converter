// PositionExtractor.ts — folds IR `Position` properties into a PositionConfig.
// Every variant in parsing/css/properties/longhands/layout/**/PositionPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { PositionConfig, POSITION_PROPERTY_TYPE } from './PositionConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractPosition(properties: IRPropertyLike[]): PositionConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, POSITION_PROPERTY_TYPE, kebab) };
}
