// OverlayExtractor.ts — folds IR `Overlay` properties into a OverlayConfig.
// Every variant in parsing/css/properties/longhands/layout/**/OverlayPropertyParser.kt
// is a single keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.

import { OverlayConfig, OVERLAY_PROPERTY_TYPE } from './OverlayConfig';
import { foldLast, kebab, type IRPropertyLike } from './_shared';

export function extractOverlay(properties: IRPropertyLike[]): OverlayConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, OVERLAY_PROPERTY_TYPE, kebab) };
}
