// ClearExtractor.ts — folds IR `Clear` properties into a ClearConfig.
// Every variant in parsing/css/properties/longhands/layout/**/ClearPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { ClearConfig, CLEAR_PROPERTY_TYPE } from './ClearConfig';
import { foldLast, kebab, type IRPropertyLike } from './_shared';

export function extractClear(properties: IRPropertyLike[]): ClearConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, CLEAR_PROPERTY_TYPE, kebab) };
}
