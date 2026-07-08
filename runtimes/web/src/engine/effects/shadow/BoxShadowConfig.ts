// BoxShadowConfig.ts — typed record for the `box-shadow` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/effects/shadow/BoxShadowPropertyParser.kt.
// Lives under engine/effects/shadow/ to match irmodels/properties/effects/shadow/
// (the per-platform folder-structure contract from CLAUDE.md).

import type { LengthValue } from '../../core/types/LengthValue';          // shared length alphabet
import type { ColorValue } from '../../core/types/ColorValue';            // shared color alphabet

// One box-shadow layer.  All length fields are optional because the CSS
// parser only emits values that were present in the source declaration:
//   "8px 8px #111"           -> {x, y, c}
//   "4px 4px 12px #111"      -> {x, y, blur, c}
//   "inset 4px 4px 8px #111" -> {x, y, blur, c, inset:true}
export interface BoxShadowLayer {
  x: LengthValue;                                                          // horizontal offset (px/em/...)
  y: LengthValue;                                                          // vertical offset
  blur?: LengthValue;                                                      // blur radius (spec default 0)
  spread?: LengthValue;                                                    // spread radius (spec default 0)
  color?: ColorValue;                                                      // shadow color (spec default currentColor)
  inset?: boolean;                                                         // inner shadow flag
}

// Config holds an ordered list of layers (CSS paints earliest-first-on-top),
// OR a raw CSS string for cases the parser couldn't decompose (e.g.
// "calc(2px + 2px) calc(2px + 2px) 8px #111" — see note on parser gap).
export interface BoxShadowConfig {
  layers?: BoxShadowLayer[];                                               // zero or more layers
  raw?: string;                                                            // fallback: emit as-is
  none?: boolean;                                                          // explicit `none` keyword
}

// IR property type string — used by extractor + registry.
export const BOX_SHADOW_PROPERTY_TYPE = 'BoxShadow' as const;
export type BoxShadowPropertyType = typeof BOX_SHADOW_PROPERTY_TYPE;
