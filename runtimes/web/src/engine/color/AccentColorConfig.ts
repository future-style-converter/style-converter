// AccentColorConfig.ts — typed record for the CSS `accent-color` property.
// IR payload is either { type: 'auto' } or { type: 'color', ... } where the
// 'color' variant carries the usual IRColor shape (srgb + original).

import type { ColorValue } from '../core/types/ColorValue';

// Discriminated union: 'auto' is a distinct sentinel, otherwise a ColorValue.
export type AccentColorMode =
  | { kind: 'auto' }                                                  // browser-default accent
  | { kind: 'color'; color: ColorValue };                             // user-specified color

// Config slot — optional so absence is still meaningful.
export interface AccentColorConfig {
  mode?: AccentColorMode;                                             // 'auto' | 'color' | unset
}

// IR property type recognised by this module.
export const ACCENT_COLOR_PROPERTY_TYPE = 'AccentColor' as const;
export type AccentColorPropertyType = typeof ACCENT_COLOR_PROPERTY_TYPE;
