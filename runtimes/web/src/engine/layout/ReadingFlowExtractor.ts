// ReadingFlowExtractor.ts — folds IR `ReadingFlow` properties into a ReadingFlowConfig.
// Every variant in parsing/css/properties/longhands/layout/**/ReadingFlowPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { ReadingFlowConfig, READINGFLOW_PROPERTY_TYPE } from './ReadingFlowConfig';
import { foldLast, kebab, type IRPropertyLike } from './_shared';

export function extractReadingFlow(properties: IRPropertyLike[]): ReadingFlowConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, READINGFLOW_PROPERTY_TYPE, kebab) };
}
