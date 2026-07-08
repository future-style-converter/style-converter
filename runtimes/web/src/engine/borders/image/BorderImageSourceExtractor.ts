// BorderImageSourceExtractor.ts — folds `BorderImageSource` IR properties into a config.
// IR shape flavors (from border-image.json):
//   {type:'none'}                         the `none` keyword
//   {type:'url', url:'border.png'}        url(...) token
//   {type:'gradient', gradient:'linear-gradient(...)'}  raw gradient CSS

import { parseBorderImageSource } from './_shared';                             // shared parse/validate
import type { BorderImageSourceConfig, BorderImageSourcePropertyType } from './BorderImageSourceConfig';
import { BORDER_IMAGE_SOURCE_PROPERTY_TYPE } from './BorderImageSourceConfig';

// Minimal IRProperty shape — keeps engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isBorderImageSourceProperty(type: string): type is BorderImageSourcePropertyType {
  return type === BORDER_IMAGE_SOURCE_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractBorderImageSource(properties: IRPropertyLike[]): BorderImageSourceConfig {
  const cfg: BorderImageSourceConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isBorderImageSourceProperty(p.type)) continue;                              // skip unrelated
    const v = parseBorderImageSource(p.data);                                                     // validate & parse
    if (v) cfg.source = v;                                           // last recognised wins
  }
  return cfg;
}
