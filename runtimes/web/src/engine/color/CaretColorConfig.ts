// CaretColorConfig.ts — typed record for the CSS `caret-color` property.
// Same shape as AccentColor: either { type: 'auto' } or { type: 'color', ... }.

import type { ColorValue } from '../core/types/ColorValue';

// Discriminated union — 'auto' keeps UA default; 'color' is user-specified.
export type CaretColorMode =
  | { kind: 'auto' }
  | { kind: 'color'; color: ColorValue };

// Config holder with optional mode.
export interface CaretColorConfig {
  mode?: CaretColorMode;                                              // 'auto' | 'color' | unset
}

// IR property type recognised by this module.
export const CARET_COLOR_PROPERTY_TYPE = 'CaretColor' as const;
export type CaretColorPropertyType = typeof CARET_COLOR_PROPERTY_TYPE;
