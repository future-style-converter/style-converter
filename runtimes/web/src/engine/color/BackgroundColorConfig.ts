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
  // css-cascade-5 §7.3.2 — the CSS-wide keyword `inherit`. It is NOT a colour,
  // so it can never travel inside `color` (ColorValue has no slot for a
  // cascade keyword and `extractColor` correctly reports it as `unknown`).
  // It is its own resolution instruction: "take the PARENT's computed value
  // of this property". background-color is a non-inherited property, so the
  // keyword is the only way a child can pick the parent's value up — and it
  // picks up the parent's COMPUTED value, which for `currentcolor` (and for a
  // `color-mix()` containing it) is still the unresolved keyword per
  // css-color-4 §6.4 ("computes to itself"). That per-element re-resolution
  // is exactly what css-color/currentcolor-002 and color-mix-currentcolor-001
  // /002 assert, so the flag must survive to the applier instead of being
  // flattened into a resolved colour here.
  inherit?: boolean;
}

// IR property type this module handles; used by the registry + extractor.
export const BACKGROUND_COLOR_PROPERTY_TYPE = 'BackgroundColor' as const;
export type BackgroundColorPropertyType = typeof BACKGROUND_COLOR_PROPERTY_TYPE;
