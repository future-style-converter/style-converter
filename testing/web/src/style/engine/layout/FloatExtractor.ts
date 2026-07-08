// FloatExtractor.ts — folds IR `Float` properties into a FloatConfig.
// Every variant in parsing/css/properties/longhands/layout/**/FloatPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { FloatConfig, FLOAT_PROPERTY_TYPE } from './FloatConfig';
import { foldLast, kebab, type IRPropertyLike } from './_shared';

export function extractFloat(properties: IRPropertyLike[]): FloatConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, FLOAT_PROPERTY_TYPE, kebab) };
}
