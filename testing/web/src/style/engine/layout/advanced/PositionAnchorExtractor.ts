// PositionAnchorExtractor.ts — folds IR `PositionAnchor` properties into a PositionAnchorConfig.
// Every variant in parsing/css/properties/longhands/layout/**/PositionAnchorPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { PositionAnchorConfig, POSITIONANCHOR_PROPERTY_TYPE } from './PositionAnchorConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractPositionAnchor(properties: IRPropertyLike[]): PositionAnchorConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, POSITIONANCHOR_PROPERTY_TYPE, kebab) };
}
