// BackgroundColorConfig.ts — typed record for the `background-color` IR property.
// Web rendering is native: the browser understands every color syntax we care
// about (hex/rgb/hsl/hwb/lab/lch/oklch/color/color-mix/light-dark/relative).
// So the config is just a ColorValue (or absent) — serialisation is trivial.

// Reuse the Phase-1 ColorValue discriminated union so every color-valued
// property in the engine speaks the same alphabet.
import type { ColorValue } from '../core/types/ColorValue';

// Simple holder — `color` is optional so callers can detect "not set".
export interface BackgroundColorConfig {
  color?: ColorValue;                                                 // parsed IRColor or absent
}

// IR property type this module handles; used by the registry + extractor.
export const BACKGROUND_COLOR_PROPERTY_TYPE = 'BackgroundColor' as const;
export type BackgroundColorPropertyType = typeof BACKGROUND_COLOR_PROPERTY_TYPE;
