// DisplayExtractor.ts — folds IR `Display` properties into a DisplayConfig.
// Every variant in parsing/css/properties/longhands/layout/**/DisplayPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { DisplayConfig, DISPLAY_PROPERTY_TYPE } from './DisplayConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

export function extractDisplay(properties: IRPropertyLike[]): DisplayConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, DISPLAY_PROPERTY_TYPE, kebab) };
}
