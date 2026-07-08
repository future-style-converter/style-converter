// PositionTryOrderExtractor.ts — folds IR `PositionTryOrder` properties into a PositionTryOrderConfig.
// Every variant in parsing/css/properties/longhands/layout/**/PositionTryOrderPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { PositionTryOrderConfig, POSITIONTRYORDER_PROPERTY_TYPE } from './PositionTryOrderConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractPositionTryOrder(properties: IRPropertyLike[]): PositionTryOrderConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, POSITIONTRYORDER_PROPERTY_TYPE, kebab) };
}
